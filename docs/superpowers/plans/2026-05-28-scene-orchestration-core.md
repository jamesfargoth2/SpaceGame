# Scene Orchestration Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `SceneManager` + transition state machine that additively loads/unloads gameplay scenes (deep space → orbit → planet surface → station/ship interior), wiring the *existing* `GalacticAssetManager`, `CoordinateManager`, `EventBus`, and per-ship interior physics worlds together via single-Engine scene-membership tagging.

**Architecture:** One Ashley `Engine` (unchanged). Each streamed entity carries a `SceneComponent { int sceneId }`; a small set carry `PersistentSceneMemberComponent` and survive scene swaps by re-tagging. A `SceneManager` owns the active-scene set and delegates choreography to a `SceneTransitionController` finite state machine (`REQUESTED → PRELOADING → READY_OVERLAP → ACTIVATING → UNLOADING_OLD → IDLE`). Loading is abstracted behind a `SceneLoader` interface so the whole engine is unit-testable headless with fakes (no GL, no procgen). Asset acquisition goes through a narrow `SceneAssetSource` seam (the real one adapts `GalacticAssetManager`; tests fake it). Transitions are async (time-sliced `step(budgetMs)`) and gameplay-disguised (event-driven, with a timeout backstop).

**Tech Stack:** Java 17, libGDX 1.13 + Ashley ECS, gdx-bullet, gdx-gltf (`SceneAsset`), JUnit 5 (`org.junit.jupiter`). Build: Gradle multi-module; tests run with `gradlew.bat :core:test`.

**Scope note:** This is **Sub-project A** of three (B = LOD bands, C = client↔server zone alignment — separate specs). This plan ships the orchestration engine fully working and tested, wired into `GameWorld` with a real `DeepSpaceLoader` and a complete `EmptySceneLoader` fallback for every other `SceneType`. Bespoke procgen-backed loaders (Orbital/PlanetSurface/StationInterior/ShipInterior calling `StarSystemGenerator`/`SpaceStationGenerator`/`ShipInteriorPhysicsSystem`) are **deferred** — they slot into the `SceneLoader` registry without touching the engine. See "Deferred" at the end.

**Conventions observed:** new code lives in package `com.galacticodyssey.core.scene` (managers, loaders, system, components, value types) and `com.galacticodyssey.core.events` (event DTOs, alongside the existing `OriginRebasedEvent`). Tests mirror the headless `new Engine()` style of `DebrisLODSystemTest`.

---

## File Structure

**Created (main):** all under `core/src/main/java/com/galacticodyssey/`
- `core/scene/SceneType.java` — enum of scene kinds
- `core/scene/SceneState.java` — enum: UNLOADED/LOADING/ACTIVE/UNLOADING
- `core/scene/SceneComponent.java` — Ashley component: `int sceneId`
- `core/scene/PersistentSceneMemberComponent.java` — Ashley marker component
- `core/scene/SceneDistanceTrigger.java` — pure hysteresis evaluator
- `core/scene/Scene.java` — scene data holder
- `core/scene/SceneTransitionRequest.java` — request value object
- `core/scene/TransitionPhase.java` — enum of FSM phases
- `core/scene/SceneAssetSource.java` — functional interface for asset acquisition
- `core/scene/SceneLoader.java` — load/unload strategy interface
- `core/scene/EmptySceneLoader.java` — complete no-entity loader (prefetch + tag cleanup)
- `core/scene/DeepSpaceLoader.java` — concrete deep-space loader
- `core/scene/SceneTransitionController.java` — the FSM
- `core/scene/SceneManager.java` — facade + active-scene registry
- `core/scene/SceneStreamingSystem.java` — Ashley `EntitySystem` bridge
- `core/events/SceneTransitionBeganEvent.java`
- `core/events/SceneLoadProgressEvent.java`
- `core/events/SceneTransitionReadyEvent.java`
- `core/events/SceneActivatedEvent.java`
- `core/events/SceneTransitionCompletedEvent.java`
- `core/events/SceneTransitionRejectedEvent.java`
- `core/events/SceneLoadFailedEvent.java`

**Created (test):** under `core/src/test/java/com/galacticodyssey/core/scene/`
- `SceneComponentTest.java`, `SceneDistanceTriggerTest.java`, `SceneEventsTest.java`, `SceneTest.java`, `SceneLoaderTest.java`, `SceneTransitionControllerTest.java`, `SceneManagerTest.java`, `SceneStreamingSystemTest.java`
- `support/FakeSceneLoader.java`, `support/FakeSceneAssetSource.java` — shared test doubles

**Modified (main):**
- `core/GameWorld.java` — construct `SceneManager` + register `SceneStreamingSystem`

---

## Task 1: Scene enums & components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneType.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneState.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/PersistentSceneMemberComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneComponentTest.java`

- [ ] **Step 1: Write the failing test**

`core/src/test/java/com/galacticodyssey/core/scene/SceneComponentTest.java`:
```java
package com.galacticodyssey.core.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneComponentTest {

    @Test
    void componentDefaultsToZeroAndStoresId() {
        SceneComponent c = new SceneComponent();
        assertEquals(0, c.sceneId);
        SceneComponent c2 = new SceneComponent(7);
        assertEquals(7, c2.sceneId);
    }

    @Test
    void persistentMarkerIsAComponent() {
        PersistentSceneMemberComponent m = new PersistentSceneMemberComponent();
        assertTrue(m instanceof com.badlogic.ashley.core.Component);
    }

    @Test
    void sceneTypeAndStateHaveExpectedValues() {
        assertEquals(6, SceneType.values().length);
        assertNotNull(SceneType.valueOf("STATION_INTERIOR"));
        assertEquals(SceneState.UNLOADED, SceneState.values()[0]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneComponentTest"`
Expected: FAIL — `SceneComponent`, `SceneType`, etc. do not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

`SceneType.java`:
```java
package com.galacticodyssey.core.scene;

/** The kind of loaded gameplay context a {@link Scene} represents. Extend as new contexts are added. */
public enum SceneType {
    DEEP_SPACE,
    ORBITAL,
    PLANET_SURFACE,
    STATION_INTERIOR,
    SHIP_INTERIOR,
    ASTEROID_FIELD
}
```

`SceneState.java`:
```java
package com.galacticodyssey.core.scene;

/** Lifecycle state of a {@link Scene}. */
public enum SceneState {
    UNLOADED,
    LOADING,
    ACTIVE,
    UNLOADING
}
```

`SceneComponent.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Component;

/** Tags an entity with the id of the {@link Scene} that owns it, so scene unload is deterministic. */
public class SceneComponent implements Component {
    public int sceneId;

    public SceneComponent() {}

    public SceneComponent(int sceneId) {
        this.sceneId = sceneId;
    }
}
```

`PersistentSceneMemberComponent.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Component;

/**
 * Marks an entity (player, active ship) that survives scene swaps. On transition it is
 * re-tagged to the destination scene instead of being unloaded with the source scene.
 */
public class PersistentSceneMemberComponent implements Component {
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneComponentTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneType.java core/src/main/java/com/galacticodyssey/core/scene/SceneState.java core/src/main/java/com/galacticodyssey/core/scene/SceneComponent.java core/src/main/java/com/galacticodyssey/core/scene/PersistentSceneMemberComponent.java core/src/test/java/com/galacticodyssey/core/scene/SceneComponentTest.java
git commit -m "feat(scene): scene type/state enums and membership components"
```

---

## Task 2: SceneDistanceTrigger (hysteresis)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneDistanceTrigger.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneDistanceTriggerTest.java`

- [ ] **Step 1: Write the failing test**

`SceneDistanceTriggerTest.java`:
```java
package com.galacticodyssey.core.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneDistanceTriggerTest {

    // enterRadius 1000 < exitRadius 1500 (hysteresis band)
    private final SceneDistanceTrigger trigger = new SceneDistanceTrigger(1000f, 1500f);

    @Test
    void entersOnlyWhenInsideEnterRadius() {
        // currently outside, distance still within the band -> must NOT enter yet
        assertFalse(trigger.shouldBeInside(false, 1200f));
        // inside enter radius -> enter
        assertTrue(trigger.shouldBeInside(false, 900f));
    }

    @Test
    void staysInsideThroughHysteresisBand() {
        // currently inside, distance in the band -> stay inside (no thrash)
        assertTrue(trigger.shouldBeInside(true, 1200f));
        // only leaves once beyond exit radius
        assertFalse(trigger.shouldBeInside(true, 1600f));
    }

    @Test
    void rejectsNonHysteresisRadii() {
        assertThrows(IllegalArgumentException.class, () -> new SceneDistanceTrigger(1500f, 1000f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneDistanceTriggerTest"`
Expected: FAIL — `SceneDistanceTrigger` does not exist.

- [ ] **Step 3: Write minimal implementation**

`SceneDistanceTrigger.java`:
```java
package com.galacticodyssey.core.scene;

/**
 * Pure distance threshold with hysteresis to prevent transition thrash at a scene boundary.
 * Enter only when distance &lt; enterRadius; once inside, stay until distance &gt; exitRadius.
 */
public final class SceneDistanceTrigger {

    private final float enterRadius;
    private final float exitRadius;

    public SceneDistanceTrigger(float enterRadius, float exitRadius) {
        if (enterRadius >= exitRadius) {
            throw new IllegalArgumentException(
                "enterRadius (" + enterRadius + ") must be < exitRadius (" + exitRadius + ") for hysteresis");
        }
        this.enterRadius = enterRadius;
        this.exitRadius = exitRadius;
    }

    /**
     * @param currentlyInside whether the player is currently treated as inside the boundary
     * @param distance        current distance to the boundary body
     * @return whether the player should now be treated as inside
     */
    public boolean shouldBeInside(boolean currentlyInside, float distance) {
        if (currentlyInside) {
            return distance <= exitRadius;
        }
        return distance < enterRadius;
    }

    public float getEnterRadius() { return enterRadius; }
    public float getExitRadius() { return exitRadius; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneDistanceTriggerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneDistanceTrigger.java core/src/test/java/com/galacticodyssey/core/scene/SceneDistanceTriggerTest.java
git commit -m "feat(scene): hysteresis distance trigger for auto transitions"
```

---

## Task 3: Transition event DTOs

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/events/SceneTransitionBeganEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SceneLoadProgressEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SceneTransitionReadyEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SceneActivatedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SceneTransitionCompletedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SceneTransitionRejectedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SceneLoadFailedEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneEventsTest.java`

- [ ] **Step 1: Write the failing test**

`SceneEventsTest.java`:
```java
package com.galacticodyssey.core.scene;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SceneEventsTest {

    @Test
    void eventsCarryDataAndDispatchThroughBus() {
        EventBus bus = new EventBus();

        AtomicReference<SceneActivatedEvent> got = new AtomicReference<>();
        bus.subscribe(SceneActivatedEvent.class, got::set);
        bus.publish(new SceneActivatedEvent(3, SceneType.ORBITAL));
        assertEquals(3, got.get().sceneId);
        assertEquals(SceneType.ORBITAL, got.get().type);

        SceneTransitionBeganEvent began = new SceneTransitionBeganEvent(SceneType.DEEP_SPACE, SceneType.ORBITAL);
        assertEquals(SceneType.DEEP_SPACE, began.from);
        assertEquals(SceneType.ORBITAL, began.to);

        assertEquals(0.5f, new SceneLoadProgressEvent(1, 0.5f).progress, 1e-6);
        assertEquals(2, new SceneTransitionReadyEvent(2).sceneId);
        assertEquals(SceneType.PLANET_SURFACE, new SceneTransitionCompletedEvent(SceneType.PLANET_SURFACE).type);
        assertEquals("busy", new SceneTransitionRejectedEvent("busy").reason);
        SceneLoadFailedEvent failed = new SceneLoadFailedEvent(SceneType.SHIP_INTERIOR, "boom");
        assertEquals(SceneType.SHIP_INTERIOR, failed.type);
        assertEquals("boom", failed.reason);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneEventsTest"`
Expected: FAIL — event classes do not exist.

- [ ] **Step 3: Write minimal implementation**

`SceneTransitionBeganEvent.java`:
```java
package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when a transition leaves IDLE. {@code from} is null for the first scene load. */
public final class SceneTransitionBeganEvent {
    public final SceneType from;
    public final SceneType to;

    public SceneTransitionBeganEvent(SceneType from, SceneType to) {
        this.from = from;
        this.to = to;
    }
}
```

`SceneLoadProgressEvent.java`:
```java
package com.galacticodyssey.core.events;

/** Published each preload step. {@code progress} is cumulative in [0,1]. */
public final class SceneLoadProgressEvent {
    public final int sceneId;
    public final float progress;

    public SceneLoadProgressEvent(int sceneId, float progress) {
        this.sceneId = sceneId;
        this.progress = progress;
    }
}
```

`SceneTransitionReadyEvent.java`:
```java
package com.galacticodyssey.core.events;

/**
 * Published when the target scene is fully loaded and both scenes are active. Disguise systems
 * (camera/VFX/animation) subscribe and call {@code SceneManager.notifyDisguiseComplete(sceneId)}
 * when their animation finishes.
 */
public final class SceneTransitionReadyEvent {
    public final int sceneId;

    public SceneTransitionReadyEvent(int sceneId) {
        this.sceneId = sceneId;
    }
}
```

`SceneActivatedEvent.java`:
```java
package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when the target scene becomes the primary scene (persistent entities re-tagged). */
public final class SceneActivatedEvent {
    public final int sceneId;
    public final SceneType type;

    public SceneActivatedEvent(int sceneId, SceneType type) {
        this.sceneId = sceneId;
        this.type = type;
    }
}
```

`SceneTransitionCompletedEvent.java`:
```java
package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when the source scene has been unloaded and the transition returns to IDLE. */
public final class SceneTransitionCompletedEvent {
    public final SceneType type;

    public SceneTransitionCompletedEvent(SceneType type) {
        this.type = type;
    }
}
```

`SceneTransitionRejectedEvent.java`:
```java
package com.galacticodyssey.core.events;

/** Published when a transition request is refused (already in progress, or max scenes reached). */
public final class SceneTransitionRejectedEvent {
    public final String reason;

    public SceneTransitionRejectedEvent(String reason) {
        this.reason = reason;
    }
}
```

`SceneLoadFailedEvent.java`:
```java
package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when a preload throws; the transition rolls back and the source scene stays active. */
public final class SceneLoadFailedEvent {
    public final SceneType type;
    public final String reason;

    public SceneLoadFailedEvent(SceneType type, String reason) {
        this.type = type;
        this.reason = reason;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneEventsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/events/Scene*.java core/src/test/java/com/galacticodyssey/core/scene/SceneEventsTest.java
git commit -m "feat(scene): transition lifecycle event DTOs"
```

---

## Task 4: Scene data holder, request & phase enum

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/scene/Scene.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionRequest.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/TransitionPhase.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneTest.java`

- [ ] **Step 1: Write the failing test**

`SceneTest.java`:
```java
package com.galacticodyssey.core.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneTest {

    @Test
    void sceneStartsUnloadedWithNoAssetsOrInteriorWorld() {
        Scene s = new Scene(5, SceneType.ORBITAL, new double[]{10.0, 20.0, 30.0});
        assertEquals(5, s.id);
        assertEquals(SceneType.ORBITAL, s.type);
        assertEquals(SceneState.UNLOADED, s.state);
        assertEquals(0, s.assets.size);
        assertNull(s.interiorWorld);
        assertArrayEquals(new double[]{10.0, 20.0, 30.0}, s.galaxyAnchor, 1e-9);
    }

    @Test
    void requestExposesTargetAndAnchor() {
        SceneTransitionRequest r = new SceneTransitionRequest(SceneType.PLANET_SURFACE, new double[]{1, 2, 3});
        assertEquals(SceneType.PLANET_SURFACE, r.targetType);
        assertArrayEquals(new double[]{1, 2, 3}, r.galaxyAnchor, 1e-9);
    }

    @Test
    void phaseEnumStartsAtIdle() {
        assertEquals(TransitionPhase.IDLE, TransitionPhase.values()[0]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTest"`
Expected: FAIL — `Scene`, `SceneTransitionRequest`, `TransitionPhase` do not exist.

- [ ] **Step 3: Write minimal implementation**

`TransitionPhase.java`:
```java
package com.galacticodyssey.core.scene;

/** Phases of the single in-flight scene transition. */
public enum TransitionPhase {
    IDLE,
    REQUESTED,
    PRELOADING,
    READY_OVERLAP,
    ACTIVATING,
    UNLOADING_OLD
}
```

`Scene.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Lifecycle data holder for one loaded gameplay context. Pure data — loading/unloading is
 * delegated to a {@link SceneLoader}. The galaxy anchor is the 64-bit double-precision origin
 * of this scene in galaxy space; loaders convert it to local float space via CoordinateManager.
 */
public final class Scene {
    public final int id;
    public final SceneType type;
    public final double[] galaxyAnchor;

    public SceneState state = SceneState.UNLOADED;
    public final Array<AssetHandle<SceneAsset>> assets = new Array<>();
    /** Non-null only for interior scenes that own a separate Bullet world. */
    public btDynamicsWorld interiorWorld;

    public Scene(int id, SceneType type, double[] galaxyAnchor) {
        this.id = id;
        this.type = type;
        this.galaxyAnchor = galaxyAnchor;
    }
}
```

`SceneTransitionRequest.java`:
```java
package com.galacticodyssey.core.scene;

/** A request to transition the primary scene to {@code targetType} anchored at {@code galaxyAnchor}. */
public final class SceneTransitionRequest {
    public final SceneType targetType;
    public final double[] galaxyAnchor;

    public SceneTransitionRequest(SceneType targetType, double[] galaxyAnchor) {
        this.targetType = targetType;
        this.galaxyAnchor = galaxyAnchor;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/Scene.java core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionRequest.java core/src/main/java/com/galacticodyssey/core/scene/TransitionPhase.java core/src/test/java/com/galacticodyssey/core/scene/SceneTest.java
git commit -m "feat(scene): Scene holder, transition request, phase enum"
```

---

## Task 5: SceneLoader seam, EmptySceneLoader & DeepSpaceLoader

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneAssetSource.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneLoader.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/EmptySceneLoader.java`
- Create: `core/src/main/java/com/galacticodyssey/core/scene/DeepSpaceLoader.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/support/FakeSceneAssetSource.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneLoaderTest.java`

**Why the `SceneAssetSource` seam:** `GalacticAssetManager.enqueue(...)` dereferences its internal libGDX `AssetManager` (`inner.isLoaded(...)`), which is null under the GL-skipping test constructor — so loaders must not call it directly in unit tests. Loaders depend on the narrow `SceneAssetSource` functional interface; `GameWorld` supplies the real adapter `(id, cat) -> assetManager.enqueue(id, cat, 0f)` (Task 10), and tests supply a fake.

- [ ] **Step 1: Write the failing test**

`core/src/test/java/com/galacticodyssey/core/scene/support/FakeSceneAssetSource.java`:
```java
package com.galacticodyssey.core.scene.support;

import com.galacticodyssey.core.scene.SceneAssetSource;
import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.ArrayList;
import java.util.List;

/** Headless asset source: hands back retained handles and records every acquire. */
public final class FakeSceneAssetSource implements SceneAssetSource {
    public final List<String> acquired = new ArrayList<>();

    @Override
    public AssetHandle<SceneAsset> acquire(String assetId, AssetCategory category) {
        acquired.add(assetId);
        AssetHandle<SceneAsset> handle = new AssetHandle<>(assetId, category, h -> {});
        return handle.retain();
    }
}
```

`core/src/test/java/com/galacticodyssey/core/scene/SceneLoaderTest.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.scene.support.FakeSceneAssetSource;
import com.galacticodyssey.data.AssetCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneLoaderTest {

    @Test
    void emptyLoaderPrefetchesDeclaredAssetsAndCompletesImmediately() {
        Engine engine = new Engine();
        FakeSceneAssetSource source = new FakeSceneAssetSource();
        EmptySceneLoader loader = new EmptySceneLoader(SceneType.STATION_INTERIOR, engine, source,
            List.of("prop_crate"), AssetCategory.PROP_SMALL);

        Scene scene = new Scene(1, SceneType.STATION_INTERIOR, new double[]{0, 0, 0});
        loader.begin(scene);
        assertEquals(List.of("prop_crate"), source.acquired);
        assertEquals(1, scene.assets.size);
        assertEquals(1f, loader.step(scene, 8f), 1e-6);
    }

    @Test
    void unloadReleasesHandlesAndRemovesTaggedEntities() {
        Engine engine = new Engine();
        FakeSceneAssetSource source = new FakeSceneAssetSource();
        EmptySceneLoader loader = new EmptySceneLoader(SceneType.STATION_INTERIOR, engine, source,
            List.of("prop_crate"), AssetCategory.PROP_SMALL);

        Scene scene = new Scene(42, SceneType.STATION_INTERIOR, new double[]{0, 0, 0});
        loader.begin(scene);

        // An entity tagged as belonging to this scene, plus one belonging to another scene.
        Entity mine = new Entity();
        mine.add(new SceneComponent(42));
        engine.addEntity(mine);
        Entity other = new Entity();
        other.add(new SceneComponent(99));
        engine.addEntity(other);

        assertEquals(1, scene.assets.get(0).getRefCount());
        loader.unload(scene);

        assertEquals(0, scene.assets.get(0).getRefCount());
        // Only the scene-42 entity is removed.
        assertEquals(1, engine.getEntitiesFor(Family.all(SceneComponent.class).get()).size());
        assertEquals(99, engine.getEntitiesFor(Family.all(SceneComponent.class).get())
            .first().getComponent(SceneComponent.class).sceneId);
    }

    @Test
    void deepSpaceLoaderReportsItsType() {
        DeepSpaceLoader loader = new DeepSpaceLoader(new Engine(), new FakeSceneAssetSource());
        assertEquals(SceneType.DEEP_SPACE, loader.type());
        Scene scene = new Scene(1, SceneType.DEEP_SPACE, new double[]{0, 0, 0});
        loader.begin(scene);
        assertEquals(1f, loader.step(scene, 8f), 1e-6);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneLoaderTest"`
Expected: FAIL — `SceneAssetSource`, `SceneLoader`, `EmptySceneLoader`, `DeepSpaceLoader` do not exist.

- [ ] **Step 3: Write minimal implementation**

`SceneAssetSource.java`:
```java
package com.galacticodyssey.core.scene;

import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/** Narrow seam over GalacticAssetManager so loaders are unit-testable without a GL context. */
@FunctionalInterface
public interface SceneAssetSource {
    /** Acquire (retain) a handle for the asset; the caller releases it on scene unload. */
    AssetHandle<SceneAsset> acquire(String assetId, AssetCategory category);
}
```

`SceneLoader.java`:
```java
package com.galacticodyssey.core.scene;

/**
 * Strategy that loads/unloads the entities, assets, and (for interiors) physics world of one
 * {@link SceneType}. Implementations must never block: {@link #step} is time-sliced.
 */
public interface SceneLoader {

    SceneType type();

    /** Called once when preloading begins (acquire assets, kick off generation). */
    void begin(Scene scene);

    /**
     * Advance loading within the given millisecond budget.
     * @return cumulative progress in [0,1]; 1.0 means fully loaded.
     * @throws RuntimeException to signal an unrecoverable load failure (triggers rollback).
     */
    float step(Scene scene, float budgetMs);

    /** Release the scene's assets, remove its entities, and dispose any interior physics world. */
    void unload(Scene scene);
}
```

`EmptySceneLoader.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.List;

/**
 * A complete, no-entity loader: prefetches a fixed list of assets and, on unload, releases them
 * and removes any entities tagged with this scene's id. Used as the registry fallback for scene
 * types that do not yet have a bespoke procgen-backed loader, so the orchestration engine runs
 * end-to-end for every {@link SceneType} today.
 */
public class EmptySceneLoader implements SceneLoader {

    private final SceneType type;
    private final Engine engine;
    private final SceneAssetSource assetSource;
    private final List<String> prefetchAssetIds;
    private final AssetCategory prefetchCategory;

    private static final Family SCENE_FAMILY = Family.all(SceneComponent.class).get();

    public EmptySceneLoader(SceneType type, Engine engine, SceneAssetSource assetSource,
                            List<String> prefetchAssetIds, AssetCategory prefetchCategory) {
        this.type = type;
        this.engine = engine;
        this.assetSource = assetSource;
        this.prefetchAssetIds = prefetchAssetIds;
        this.prefetchCategory = prefetchCategory;
    }

    @Override
    public SceneType type() { return type; }

    @Override
    public void begin(Scene scene) {
        for (String id : prefetchAssetIds) {
            scene.assets.add(assetSource.acquire(id, prefetchCategory));
        }
    }

    @Override
    public float step(Scene scene, float budgetMs) {
        return 1f;
    }

    @Override
    public void unload(Scene scene) {
        for (AssetHandle<SceneAsset> handle : scene.assets) {
            handle.release();
        }
        scene.assets.clear();
        removeTaggedEntities(scene.id);
    }

    private void removeTaggedEntities(int sceneId) {
        Array<Entity> toRemove = new Array<>();
        for (Entity e : engine.getEntitiesFor(SCENE_FAMILY)) {
            if (e.getComponent(SceneComponent.class).sceneId == sceneId) {
                toRemove.add(e);
            }
        }
        for (Entity e : toRemove) {
            engine.removeEntity(e);
        }
    }
}
```

`DeepSpaceLoader.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;

import java.util.List;

/**
 * Concrete loader for the DEEP_SPACE scene: the open-flight baseline with no heavy terrain or
 * interior. It currently carries no prefetch assets (deep space streams star/ship props on
 * demand via the existing StreamingSystem) and removes its tagged entities on unload.
 */
public final class DeepSpaceLoader extends EmptySceneLoader {

    public DeepSpaceLoader(Engine engine, SceneAssetSource assetSource) {
        super(SceneType.DEEP_SPACE, engine, assetSource, List.of(), null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneLoaderTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneAssetSource.java core/src/main/java/com/galacticodyssey/core/scene/SceneLoader.java core/src/main/java/com/galacticodyssey/core/scene/EmptySceneLoader.java core/src/main/java/com/galacticodyssey/core/scene/DeepSpaceLoader.java core/src/test/java/com/galacticodyssey/core/scene/support/FakeSceneAssetSource.java core/src/test/java/com/galacticodyssey/core/scene/SceneLoaderTest.java
git commit -m "feat(scene): SceneLoader seam with empty and deep-space loaders"
```

---

## Task 6: SceneTransitionController — happy path

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionController.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/support/FakeSceneLoader.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneTransitionControllerTest.java`

The controller advances **at most one phase per `update(dt)` call** (PRELOADING repeats until the loader reports done), which keeps tests deterministic and lets the "both scenes ACTIVE during overlap" invariant be asserted frame-by-frame.

- [ ] **Step 1: Write the failing test**

`core/src/test/java/com/galacticodyssey/core/scene/support/FakeSceneLoader.java`:
```java
package com.galacticodyssey.core.scene.support;

import com.galacticodyssey.core.scene.Scene;
import com.galacticodyssey.core.scene.SceneLoader;
import com.galacticodyssey.core.scene.SceneType;

/** Scriptable loader: control how many steps until done, whether to throw, and record unloads. */
public final class FakeSceneLoader implements SceneLoader {

    private final SceneType type;
    public int stepsToComplete = 1;
    public boolean throwOnStep = false;
    public int beginCount = 0;
    public int unloadCount = 0;
    private int stepsTaken = 0;

    public FakeSceneLoader(SceneType type) {
        this.type = type;
    }

    @Override public SceneType type() { return type; }

    @Override public void begin(Scene scene) {
        beginCount++;
        stepsTaken = 0;
    }

    @Override public float step(Scene scene, float budgetMs) {
        if (throwOnStep) {
            throw new RuntimeException("simulated load failure");
        }
        stepsTaken++;
        return Math.min(1f, (float) stepsTaken / stepsToComplete);
    }

    @Override public void unload(Scene scene) {
        unloadCount++;
    }
}
```

`core/src/test/java/com/galacticodyssey/core/scene/SceneTransitionControllerTest.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.core.scene.support.FakeSceneLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneTransitionControllerTest {

    private EventBus bus;
    private Engine engine;
    private SceneTransitionController controller;
    private List<Object> events;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        engine = new Engine();
        controller = new SceneTransitionController(bus, engine);
        controller.setDisguiseTimeout(0f); // happy-path: no disguise wait unless a test sets it
        events = new ArrayList<>();
        bus.subscribe(SceneTransitionBeganEvent.class, events::add);
        bus.subscribe(SceneTransitionReadyEvent.class, events::add);
        bus.subscribe(SceneActivatedEvent.class, events::add);
        bus.subscribe(SceneTransitionCompletedEvent.class, events::add);
    }

    private Scene scene(int id, SceneType type, SceneState state) {
        Scene s = new Scene(id, type, new double[]{0, 0, 0});
        s.state = state;
        return s;
    }

    @Test
    void happyPathWalksAllPhasesAndEmitsEventsInOrder() {
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        FakeSceneLoader sourceLoader = new FakeSceneLoader(SceneType.DEEP_SPACE);
        FakeSceneLoader targetLoader = new FakeSceneLoader(SceneType.ORBITAL);

        controller.begin(source, sourceLoader, target, targetLoader);
        assertEquals(TransitionPhase.REQUESTED, controller.getPhase());

        controller.update(0.1f); // REQUESTED -> PRELOADING (begin called)
        assertEquals(TransitionPhase.PRELOADING, controller.getPhase());
        assertEquals(1, targetLoader.beginCount);
        assertEquals(SceneState.LOADING, target.state);

        controller.update(0.1f); // PRELOADING done (1 step) -> READY_OVERLAP
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());
        // both scenes active during overlap
        assertEquals(SceneState.ACTIVE, source.state);
        assertEquals(SceneState.ACTIVE, target.state);

        controller.update(0.1f); // READY_OVERLAP -> ACTIVATING (timeout 0 -> proceed)
        assertEquals(TransitionPhase.ACTIVATING, controller.getPhase());

        controller.update(0.1f); // ACTIVATING -> UNLOADING_OLD
        assertEquals(TransitionPhase.UNLOADING_OLD, controller.getPhase());

        controller.update(0.1f); // UNLOADING_OLD -> IDLE (source unloaded)
        assertEquals(TransitionPhase.IDLE, controller.getPhase());
        assertEquals(1, sourceLoader.unloadCount);
        assertEquals(SceneState.UNLOADED, source.state);
        assertTrue(controller.isIdle());

        assertEquals(4, events.size());
        assertTrue(events.get(0) instanceof SceneTransitionBeganEvent);
        assertTrue(events.get(1) instanceof SceneTransitionReadyEvent);
        assertTrue(events.get(2) instanceof SceneActivatedEvent);
        assertTrue(events.get(3) instanceof SceneTransitionCompletedEvent);
    }

    @Test
    void activatingReTagsPersistentEntitiesExactlyOnceToTarget() {
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.SHIP_INTERIOR, SceneState.UNLOADED);

        Entity player = new Entity();
        player.add(new PersistentSceneMemberComponent());
        player.add(new SceneComponent(1));
        engine.addEntity(player);

        controller.begin(source, new FakeSceneLoader(SceneType.DEEP_SPACE),
            target, new FakeSceneLoader(SceneType.SHIP_INTERIOR));
        for (int i = 0; i < 6; i++) controller.update(0.1f);

        assertEquals(2, player.getComponent(SceneComponent.class).sceneId);
        assertTrue(controller.isIdle());
    }

    @Test
    void nullSourceSkipsUnloadStep() {
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        FakeSceneLoader targetLoader = new FakeSceneLoader(SceneType.ORBITAL);
        controller.begin(null, null, target, targetLoader);
        for (int i = 0; i < 6; i++) controller.update(0.1f);
        assertTrue(controller.isIdle());
        assertEquals(SceneState.ACTIVE, target.state);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTransitionControllerTest"`
Expected: FAIL — `SceneTransitionController` does not exist.

- [ ] **Step 3: Write minimal implementation**

`SceneTransitionController.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.SceneActivatedEvent;
import com.galacticodyssey.core.events.SceneLoadProgressEvent;
import com.galacticodyssey.core.events.SceneTransitionBeganEvent;
import com.galacticodyssey.core.events.SceneTransitionCompletedEvent;
import com.galacticodyssey.core.events.SceneTransitionReadyEvent;

/**
 * Drives a single in-flight scene transition through its phases, one phase per {@link #update}
 * call (PRELOADING repeats until the target loader reports done). Re-tags persistent entities at
 * ACTIVATING and unloads the source at UNLOADING_OLD. Disguise gating (Task 7) and failure
 * rollback (Task 8) are layered on later.
 */
public class SceneTransitionController {

    private static final float DEFAULT_BUDGET_MS = 8f;

    private static final ComponentMapper<SceneComponent> SCENE_MAP =
        ComponentMapper.getFor(SceneComponent.class);
    private static final Family PERSISTENT_FAMILY =
        Family.all(PersistentSceneMemberComponent.class, SceneComponent.class).get();

    private final EventBus eventBus;
    private final Engine engine;

    private float budgetMs = DEFAULT_BUDGET_MS;
    private float disguiseTimeout = 0f;

    private TransitionPhase phase = TransitionPhase.IDLE;
    private Scene source;
    private SceneLoader sourceLoader;
    private Scene target;
    private SceneLoader targetLoader;

    public SceneTransitionController(EventBus eventBus, Engine engine) {
        this.eventBus = eventBus;
        this.engine = engine;
    }

    public void setBudgetMs(float budgetMs) { this.budgetMs = budgetMs; }
    public void setDisguiseTimeout(float seconds) { this.disguiseTimeout = seconds; }

    public boolean isIdle() { return phase == TransitionPhase.IDLE; }
    public TransitionPhase getPhase() { return phase; }
    public Scene getTargetScene() { return target; }

    /** Start a transition. {@code source}/{@code sourceLoader} may be null for the first scene. */
    public void begin(Scene source, SceneLoader sourceLoader, Scene target, SceneLoader targetLoader) {
        this.source = source;
        this.sourceLoader = sourceLoader;
        this.target = target;
        this.targetLoader = targetLoader;
        this.phase = TransitionPhase.REQUESTED;
    }

    public void update(float dt) {
        switch (phase) {
            case IDLE:
                return;
            case REQUESTED:
                targetLoader.begin(target);
                target.state = SceneState.LOADING;
                eventBus.publish(new SceneTransitionBeganEvent(
                    source != null ? source.type : null, target.type));
                phase = TransitionPhase.PRELOADING;
                return;
            case PRELOADING: {
                float progress = targetLoader.step(target, budgetMs);
                eventBus.publish(new SceneLoadProgressEvent(target.id, progress));
                if (progress >= 1f) {
                    target.state = SceneState.ACTIVE;
                    eventBus.publish(new SceneTransitionReadyEvent(target.id));
                    phase = TransitionPhase.READY_OVERLAP;
                }
                return;
            }
            case READY_OVERLAP:
                // Happy path (no disguise wait); Task 7 adds gating + timeout here.
                phase = TransitionPhase.ACTIVATING;
                return;
            case ACTIVATING:
                reTagPersistentEntities(target.id);
                eventBus.publish(new SceneActivatedEvent(target.id, target.type));
                phase = TransitionPhase.UNLOADING_OLD;
                return;
            case UNLOADING_OLD:
                if (source != null) {
                    source.state = SceneState.UNLOADING;
                    if (sourceLoader != null) sourceLoader.unload(source);
                    source.state = SceneState.UNLOADED;
                }
                eventBus.publish(new SceneTransitionCompletedEvent(target.type));
                reset();
                return;
        }
    }

    private void reTagPersistentEntities(int targetSceneId) {
        for (Entity e : engine.getEntitiesFor(PERSISTENT_FAMILY)) {
            SCENE_MAP.get(e).sceneId = targetSceneId;
        }
    }

    private void reset() {
        phase = TransitionPhase.IDLE;
        source = null;
        sourceLoader = null;
        target = null;
        targetLoader = null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTransitionControllerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionController.java core/src/test/java/com/galacticodyssey/core/scene/support/FakeSceneLoader.java core/src/test/java/com/galacticodyssey/core/scene/SceneTransitionControllerTest.java
git commit -m "feat(scene): transition controller happy-path state machine"
```

---

## Task 7: Disguise gating & timeout

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionController.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneTransitionControllerTest.java` (add tests)

Default disguise timeout is **5 seconds** (a missing/never-finishing disguise subscriber cannot soft-lock transitions). `notifyDisguiseComplete()` lets a disguise system proceed immediately.

- [ ] **Step 1: Write the failing test**

Append these tests to `SceneTransitionControllerTest.java`:
```java
    @Test
    void readyOverlapWaitsForDisguiseThenProceeds() {
        controller.setDisguiseTimeout(5f);
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        controller.begin(source, new FakeSceneLoader(SceneType.DEEP_SPACE),
            target, new FakeSceneLoader(SceneType.ORBITAL));

        controller.update(0.1f); // -> PRELOADING
        controller.update(0.1f); // -> READY_OVERLAP
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());

        controller.update(0.1f); // still waiting on disguise
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());

        controller.notifyDisguiseComplete();
        controller.update(0.1f); // disguise done -> ACTIVATING
        assertEquals(TransitionPhase.ACTIVATING, controller.getPhase());
    }

    @Test
    void readyOverlapProceedsOnTimeoutWithoutDisguiseSignal() {
        controller.setDisguiseTimeout(0.25f);
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        controller.begin(source, new FakeSceneLoader(SceneType.DEEP_SPACE),
            target, new FakeSceneLoader(SceneType.ORBITAL));

        controller.update(0.1f); // -> PRELOADING
        controller.update(0.1f); // -> READY_OVERLAP
        controller.update(0.1f); // timer 0.1 < 0.25, wait
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());
        controller.update(0.2f); // timer 0.3 >= 0.25, proceed
        assertEquals(TransitionPhase.ACTIVATING, controller.getPhase());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTransitionControllerTest"`
Expected: FAIL — `notifyDisguiseComplete()` does not exist; timeout-default test fails because `READY_OVERLAP` proceeds immediately (current default timeout 0f and no waiting logic).

- [ ] **Step 3: Write minimal implementation**

In `SceneTransitionController.java`, change the default timeout and add disguise fields. Replace:
```java
    private float disguiseTimeout = 0f;
```
with:
```java
    private float disguiseTimeout = 5f;
    private boolean disguiseComplete = false;
    private float disguiseTimer = 0f;
```

Replace the `READY_OVERLAP` case body:
```java
            case READY_OVERLAP:
                // Happy path (no disguise wait); Task 7 adds gating + timeout here.
                phase = TransitionPhase.ACTIVATING;
                return;
```
with:
```java
            case READY_OVERLAP:
                disguiseTimer += dt;
                if (disguiseComplete || disguiseTimer >= disguiseTimeout) {
                    phase = TransitionPhase.ACTIVATING;
                }
                return;
```

In `begin(...)`, reset the disguise state — add these two lines at the end of the method body (after `this.phase = TransitionPhase.REQUESTED;`):
```java
        this.disguiseComplete = false;
        this.disguiseTimer = 0f;
```

Add the notify method (e.g. after `begin`):
```java
    /** Called by a disguise system (camera/VFX/animation) when its transition animation finishes. */
    public void notifyDisguiseComplete() {
        this.disguiseComplete = true;
    }
```

> Note: the Task 6 test calls `controller.setDisguiseTimeout(0f)` in `setUp()`, so its happy-path test still proceeds immediately (timer 0 ≥ timeout 0). Leave that line in place.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTransitionControllerTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionController.java core/src/test/java/com/galacticodyssey/core/scene/SceneTransitionControllerTest.java
git commit -m "feat(scene): disguise gating with timeout backstop"
```

---

## Task 8: Failure rollback

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionController.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneTransitionControllerTest.java` (add test)

If `targetLoader.step` throws, the transition must roll back: unload the partial target (releasing acquired handles / disposing a partial interior world), leave the source `ACTIVE`, publish `SceneLoadFailedEvent`, and return to `IDLE`.

- [ ] **Step 1: Write the failing test**

Append to `SceneTransitionControllerTest.java`:
```java
    @Test
    void loadFailureRollsBackAndKeepsSourceActive() {
        List<SceneLoadFailedEvent> failures = new ArrayList<>();
        bus.subscribe(SceneLoadFailedEvent.class, failures::add);

        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.PLANET_SURFACE, SceneState.UNLOADED);
        FakeSceneLoader sourceLoader = new FakeSceneLoader(SceneType.DEEP_SPACE);
        FakeSceneLoader targetLoader = new FakeSceneLoader(SceneType.PLANET_SURFACE);
        targetLoader.throwOnStep = true;

        controller.begin(source, sourceLoader, target, targetLoader);
        controller.update(0.1f); // REQUESTED -> PRELOADING
        controller.update(0.1f); // step throws -> rollback

        assertTrue(controller.isIdle());
        assertEquals(SceneState.ACTIVE, source.state);
        assertEquals(SceneState.UNLOADED, target.state);
        assertEquals(1, targetLoader.unloadCount); // partial target cleaned up
        assertEquals(0, sourceLoader.unloadCount);  // source untouched
        assertEquals(1, failures.size());
        assertEquals(SceneType.PLANET_SURFACE, failures.get(0).type);
    }
```

(Requires the import `import com.galacticodyssey.core.events.SceneLoadFailedEvent;` — add it to the test's import block if not already present from the wildcard `com.galacticodyssey.core.events.*`. The existing test imports `com.galacticodyssey.core.events.*`, so no change needed.)

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTransitionControllerTest"`
Expected: FAIL — the `RuntimeException` from `step` propagates out of `update` (no rollback), so the assertions never run.

- [ ] **Step 3: Write minimal implementation**

In `SceneTransitionController.java`, add the import:
```java
import com.galacticodyssey.core.events.SceneLoadFailedEvent;
```

Replace the `PRELOADING` case body:
```java
            case PRELOADING: {
                float progress = targetLoader.step(target, budgetMs);
                eventBus.publish(new SceneLoadProgressEvent(target.id, progress));
                if (progress >= 1f) {
                    target.state = SceneState.ACTIVE;
                    eventBus.publish(new SceneTransitionReadyEvent(target.id));
                    phase = TransitionPhase.READY_OVERLAP;
                }
                return;
            }
```
with:
```java
            case PRELOADING: {
                float progress;
                try {
                    progress = targetLoader.step(target, budgetMs);
                } catch (RuntimeException ex) {
                    rollback(ex.getMessage());
                    return;
                }
                eventBus.publish(new SceneLoadProgressEvent(target.id, progress));
                if (progress >= 1f) {
                    target.state = SceneState.ACTIVE;
                    eventBus.publish(new SceneTransitionReadyEvent(target.id));
                    phase = TransitionPhase.READY_OVERLAP;
                }
                return;
            }
```

Add the `rollback` helper (e.g. above `reset()`):
```java
    private void rollback(String reason) {
        SceneType failedType = target.type;
        try {
            targetLoader.unload(target);
        } catch (RuntimeException ignored) {
            // best-effort cleanup; never mask the original failure
        }
        target.state = SceneState.UNLOADED;
        // source is left untouched and ACTIVE
        eventBus.publish(new SceneLoadFailedEvent(failedType, reason));
        reset();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneTransitionControllerTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneTransitionController.java core/src/test/java/com/galacticodyssey/core/scene/SceneTransitionControllerTest.java
git commit -m "feat(scene): roll back transition on load failure"
```

---

## Task 9: SceneManager facade

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneManager.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneManagerTest.java`

`SceneManager` owns the active-scene set and the controller, validates requests (single in-flight; max active scenes), picks loaders from a `Map<SceneType, SceneLoader>` (falling back to a supplied default loader), and maintains the primary scene + active set by subscribing to lifecycle events.

- [ ] **Step 1: Write the failing test**

`SceneManagerTest.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.SceneTransitionRejectedEvent;
import com.galacticodyssey.core.scene.support.FakeSceneLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SceneManagerTest {

    private EventBus bus;
    private Engine engine;
    private Map<SceneType, SceneLoader> loaders;
    private FakeSceneLoader deepLoader;
    private FakeSceneLoader orbitalLoader;
    private SceneManager manager;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        engine = new Engine();
        deepLoader = new FakeSceneLoader(SceneType.DEEP_SPACE);
        orbitalLoader = new FakeSceneLoader(SceneType.ORBITAL);
        loaders = new EnumMap<>(SceneType.class);
        loaders.put(SceneType.DEEP_SPACE, deepLoader);
        loaders.put(SceneType.ORBITAL, orbitalLoader);
        manager = new SceneManager(bus, engine, loaders, deepLoader, 3);
        manager.getController().setDisguiseTimeout(0f);
    }

    private void runToIdle() {
        for (int i = 0; i < 8 && !manager.getController().isIdle(); i++) {
            manager.update(0.1f);
        }
    }

    @Test
    void firstRequestLoadsInitialSceneWithNoSource() {
        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0})));
        runToIdle();
        assertNotNull(manager.getPrimaryScene());
        assertEquals(SceneType.DEEP_SPACE, manager.getPrimaryScene().type);
        assertEquals(1, manager.getActiveScenes().size());
    }

    @Test
    void transitionSwapsPrimaryAndUnloadsSource() {
        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        runToIdle();
        int firstId = manager.getPrimaryScene().id;

        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, new double[]{1, 0, 0})));
        runToIdle();

        assertEquals(SceneType.ORBITAL, manager.getPrimaryScene().type);
        assertNotEquals(firstId, manager.getPrimaryScene().id);
        assertEquals(1, manager.getActiveScenes().size());
        assertEquals(1, deepLoader.unloadCount);
    }

    @Test
    void rejectsConcurrentTransition() {
        List<SceneTransitionRejectedEvent> rejects = new ArrayList<>();
        bus.subscribe(SceneTransitionRejectedEvent.class, rejects::add);

        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        runToIdle();
        // Start a transition and leave it mid-flight (orbital loader needs many steps).
        orbitalLoader.stepsToComplete = 10;
        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, new double[]{1, 0, 0})));
        manager.update(0.1f); // REQUESTED -> PRELOADING, still in flight

        assertFalse(manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0})));
        assertEquals(1, rejects.size());
        assertTrue(rejects.get(0).reason.toLowerCase().contains("progress"));
    }

    @Test
    void rejectsWhenMaxActiveScenesReached() {
        List<SceneTransitionRejectedEvent> rejects = new ArrayList<>();
        bus.subscribe(SceneTransitionRejectedEvent.class, rejects::add);

        SceneManager tight = new SceneManager(bus, engine, loaders, deepLoader, 1);
        tight.getController().setDisguiseTimeout(0f);
        tight.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        for (int i = 0; i < 8 && !tight.getController().isIdle(); i++) tight.update(0.1f);

        // 1 active + 1 target = 2 > max 1 -> rejected
        assertFalse(tight.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, new double[]{1, 0, 0})));
        assertFalse(rejects.isEmpty());
        assertTrue(rejects.get(rejects.size() - 1).reason.toLowerCase().contains("max"));
    }

    @Test
    void unknownSceneTypeUsesFallbackLoader() {
        // No loader registered for PLANET_SURFACE -> fallback (deepLoader) is used; transition still completes.
        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        runToIdle();
        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.PLANET_SURFACE, new double[]{2, 0, 0})));
        runToIdle();
        assertEquals(SceneType.PLANET_SURFACE, manager.getPrimaryScene().type);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneManagerTest"`
Expected: FAIL — `SceneManager` does not exist.

- [ ] **Step 3: Write minimal implementation**

`SceneManager.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.SceneActivatedEvent;
import com.galacticodyssey.core.events.SceneLoadFailedEvent;
import com.galacticodyssey.core.events.SceneTransitionCompletedEvent;
import com.galacticodyssey.core.events.SceneTransitionRejectedEvent;

import java.util.Map;

/**
 * Facade over the scene system: owns the active-scene set and the {@link SceneTransitionController},
 * validates transition requests (single in-flight; max active scenes), resolves a {@link SceneLoader}
 * per scene type, and tracks the primary scene by observing lifecycle events.
 */
public final class SceneManager {

    private final EventBus eventBus;
    private final Map<SceneType, SceneLoader> loaders;
    private final SceneLoader fallbackLoader;
    private final int maxActiveScenes;
    private final SceneTransitionController controller;

    private final Array<Scene> activeScenes = new Array<>();
    private Scene primaryScene;
    private int nextSceneId = 1;

    public SceneManager(EventBus eventBus, Engine engine, Map<SceneType, SceneLoader> loaders,
                        SceneLoader fallbackLoader, int maxActiveScenes) {
        this.eventBus = eventBus;
        this.loaders = loaders;
        this.fallbackLoader = fallbackLoader;
        this.maxActiveScenes = maxActiveScenes;
        this.controller = new SceneTransitionController(eventBus, engine);

        eventBus.subscribe(SceneActivatedEvent.class, this::onSceneActivated);
        eventBus.subscribe(SceneTransitionCompletedEvent.class, e -> pruneUnloaded());
        eventBus.subscribe(SceneLoadFailedEvent.class, e -> pruneUnloaded());
    }

    public SceneTransitionController getController() { return controller; }
    public Scene getPrimaryScene() { return primaryScene; }
    public Array<Scene> getActiveScenes() { return activeScenes; }

    /**
     * Request a transition to a new primary scene. Returns false (and publishes a
     * {@link SceneTransitionRejectedEvent}) if a transition is already in flight or the active
     * scene budget would be exceeded.
     */
    public boolean requestTransition(SceneTransitionRequest request) {
        if (!controller.isIdle()) {
            eventBus.publish(new SceneTransitionRejectedEvent("transition already in progress"));
            return false;
        }
        if (activeScenes.size + 1 > maxActiveScenes) {
            eventBus.publish(new SceneTransitionRejectedEvent(
                "max active scenes reached (" + maxActiveScenes + ")"));
            return false;
        }
        Scene target = new Scene(nextSceneId++, request.targetType, request.galaxyAnchor);
        activeScenes.add(target);
        SceneLoader targetLoader = loaderFor(request.targetType);
        SceneLoader sourceLoader = primaryScene != null ? loaderFor(primaryScene.type) : null;
        controller.begin(primaryScene, sourceLoader, target, targetLoader);
        return true;
    }

    public void update(float dt) {
        controller.update(dt);
    }

    /** Forwarded from a disguise system once its transition animation finishes. */
    public void notifyDisguiseComplete() {
        controller.notifyDisguiseComplete();
    }

    private SceneLoader loaderFor(SceneType type) {
        return loaders.getOrDefault(type, fallbackLoader);
    }

    private void onSceneActivated(SceneActivatedEvent e) {
        for (Scene s : activeScenes) {
            if (s.id == e.sceneId) {
                primaryScene = s;
                return;
            }
        }
    }

    private void pruneUnloaded() {
        for (int i = activeScenes.size - 1; i >= 0; i--) {
            if (activeScenes.get(i).state == SceneState.UNLOADED) {
                activeScenes.removeIndex(i);
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneManagerTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneManager.java core/src/test/java/com/galacticodyssey/core/scene/SceneManagerTest.java
git commit -m "feat(scene): SceneManager facade with request validation"
```

---

## Task 10: SceneStreamingSystem + GameWorld wiring

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/scene/SceneStreamingSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/core/scene/SceneStreamingSystemTest.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

The system feeds the player position to an automatic orbital trigger (hysteresis) and pumps `SceneManager.update(dt)` each frame. For Sub-project A it carries one configurable DEEP_SPACE↔ORBITAL trigger; richer multi-body triggers come later. Player position is pushed via `setPlayerPosition` (mirroring `StreamingSystem.setCameraPosition` / `DebrisLODSystem.setPlayerPosition`), so the system is testable headless.

- [ ] **Step 1: Write the failing test**

`SceneStreamingSystemTest.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.scene.support.FakeSceneLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SceneStreamingSystemTest {

    private Engine engine;
    private SceneManager manager;
    private SceneStreamingSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        EventBus bus = new EventBus();
        FakeSceneLoader deep = new FakeSceneLoader(SceneType.DEEP_SPACE);
        FakeSceneLoader orbital = new FakeSceneLoader(SceneType.ORBITAL);
        Map<SceneType, SceneLoader> loaders = new EnumMap<>(SceneType.class);
        loaders.put(SceneType.DEEP_SPACE, deep);
        loaders.put(SceneType.ORBITAL, orbital);
        manager = new SceneManager(bus, engine, loaders, deep, 3);
        manager.getController().setDisguiseTimeout(0f);

        // Start in deep space.
        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        for (int i = 0; i < 8 && !manager.getController().isIdle(); i++) manager.update(0.1f);

        system = new SceneStreamingSystem(manager);
        system.configureOrbitalTrigger(
            new SceneDistanceTrigger(1000f, 1500f),
            new Vector3(0, 0, 0),          // body local position
            new double[]{0, 0, 0},          // orbital scene anchor
            new double[]{99999, 0, 0});     // deep-space return anchor
        engine.addSystem(system);
    }

    private void pump(int frames) {
        for (int i = 0; i < frames; i++) engine.update(0.1f);
    }

    @Test
    void crossingEnterRadiusTransitionsToOrbital() {
        system.setPlayerPosition(new Vector3(900f, 0, 0)); // within enter radius
        pump(8);
        assertEquals(SceneType.ORBITAL, manager.getPrimaryScene().type);
    }

    @Test
    void stayingInHysteresisBandDoesNotTransition() {
        system.setPlayerPosition(new Vector3(1200f, 0, 0)); // in the band, still outside
        pump(8);
        assertEquals(SceneType.DEEP_SPACE, manager.getPrimaryScene().type);
    }

    @Test
    void leavingExitRadiusReturnsToDeepSpace() {
        system.setPlayerPosition(new Vector3(900f, 0, 0));
        pump(8);
        assertEquals(SceneType.ORBITAL, manager.getPrimaryScene().type);
        system.setPlayerPosition(new Vector3(1600f, 0, 0)); // beyond exit radius
        pump(8);
        assertEquals(SceneType.DEEP_SPACE, manager.getPrimaryScene().type);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneStreamingSystemTest"`
Expected: FAIL — `SceneStreamingSystem` does not exist.

- [ ] **Step 3: Write minimal implementation**

`SceneStreamingSystem.java`:
```java
package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;

/**
 * Per-frame bridge between gameplay and the {@link SceneManager}: evaluates automatic
 * distance-based transitions (with hysteresis) and pumps the manager's transition controller.
 * Player position is pushed in via {@link #setPlayerPosition}. For Sub-project A it carries a
 * single DEEP_SPACE&lt;-&gt;ORBITAL trigger; explicit transitions (land/dock/board) arrive via
 * {@link SceneManager#requestTransition} from gameplay systems.
 */
public final class SceneStreamingSystem extends EntitySystem {

    private final SceneManager sceneManager;
    private final Vector3 playerPosition = new Vector3();

    private SceneDistanceTrigger orbitalTrigger;
    private final Vector3 orbitalBodyLocalPos = new Vector3();
    private double[] orbitalAnchor;
    private double[] deepSpaceAnchor;

    public SceneStreamingSystem(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public void setPlayerPosition(Vector3 position) {
        playerPosition.set(position);
    }

    /** Configure the deep-space &lt;-&gt; orbital auto trigger. */
    public void configureOrbitalTrigger(SceneDistanceTrigger trigger, Vector3 bodyLocalPos,
                                        double[] orbitalAnchor, double[] deepSpaceAnchor) {
        this.orbitalTrigger = trigger;
        this.orbitalBodyLocalPos.set(bodyLocalPos);
        this.orbitalAnchor = orbitalAnchor;
        this.deepSpaceAnchor = deepSpaceAnchor;
    }

    @Override
    public void update(float deltaTime) {
        evaluateAutoTriggers();
        sceneManager.update(deltaTime);
    }

    private void evaluateAutoTriggers() {
        if (orbitalTrigger == null) return;
        if (!sceneManager.getController().isIdle()) return;
        Scene primary = sceneManager.getPrimaryScene();
        if (primary == null) return;

        boolean inside = primary.type == SceneType.ORBITAL;
        float distance = playerPosition.dst(orbitalBodyLocalPos);
        boolean shouldBeInside = orbitalTrigger.shouldBeInside(inside, distance);

        if (shouldBeInside && !inside) {
            sceneManager.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, orbitalAnchor));
        } else if (!shouldBeInside && inside) {
            sceneManager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, deepSpaceAnchor));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.core.scene.SceneStreamingSystemTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/scene/SceneStreamingSystem.java core/src/test/java/com/galacticodyssey/core/scene/SceneStreamingSystemTest.java
git commit -m "feat(scene): streaming system with auto orbital trigger"
```

- [ ] **Step 6: Wire SceneManager + SceneStreamingSystem into GameWorld**

In `core/src/main/java/com/galacticodyssey/core/GameWorld.java`, add imports near the other `core.scene`/`data` imports:
```java
import com.galacticodyssey.core.scene.DeepSpaceLoader;
import com.galacticodyssey.core.scene.EmptySceneLoader;
import com.galacticodyssey.core.scene.SceneLoader;
import com.galacticodyssey.core.scene.SceneManager;
import com.galacticodyssey.core.scene.SceneStreamingSystem;
import com.galacticodyssey.core.scene.SceneTransitionRequest;
import com.galacticodyssey.core.scene.SceneType;
import com.galacticodyssey.core.scene.SceneAssetSource;
import com.galacticodyssey.data.AssetCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
```

Add fields alongside the other system/field declarations (near line 248–249, by `assetManager`/`streamingSystem`):
```java
    private SceneManager sceneManager;
    private SceneStreamingSystem sceneStreamingSystem;
```

Replace the existing "Asset streaming" block (currently lines ~603–615):
```java
        // Asset streaming
        if (com.badlogic.gdx.Gdx.files != null) {
            assetManager = new GalacticAssetManager();
            JsonReader jsonReader = new JsonReader();
            assetManager.registerManifest(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/characters.json")));
            assetManager.registerManifest(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/props.json")));
            assetManager.loadStreamingConfig(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/streaming_config.json")));
            streamingSystem = new StreamingSystem(assetManager);
            engine.addSystem(streamingSystem);
        }
```
with:
```java
        // Asset streaming
        if (com.badlogic.gdx.Gdx.files != null) {
            assetManager = new GalacticAssetManager();
            JsonReader jsonReader = new JsonReader();
            assetManager.registerManifest(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/characters.json")));
            assetManager.registerManifest(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/props.json")));
            assetManager.loadStreamingConfig(jsonReader.parse(
                com.badlogic.gdx.Gdx.files.internal("data/assets/streaming_config.json")));
            streamingSystem = new StreamingSystem(assetManager);
            engine.addSystem(streamingSystem);
        }

        // Scene orchestration: real asset source when the asset manager exists, else a no-op
        // source so the engine still runs in headless contexts.
        SceneAssetSource assetSource = (assetManager != null)
            ? (id, cat) -> assetManager.enqueue(id, cat, 0f)
            : (id, cat) -> new com.galacticodyssey.data.AssetHandle<net.mgsx.gltf.scene3d.scene.SceneAsset>(
                id, cat, h -> {}).retain();
        DeepSpaceLoader deepSpaceLoader = new DeepSpaceLoader(engine, assetSource);
        Map<SceneType, SceneLoader> sceneLoaders = new EnumMap<>(SceneType.class);
        sceneLoaders.put(SceneType.DEEP_SPACE, deepSpaceLoader);
        // Every other scene type gets a complete empty loader until its bespoke procgen loader lands.
        for (SceneType t : SceneType.values()) {
            if (!sceneLoaders.containsKey(t)) {
                sceneLoaders.put(t, new EmptySceneLoader(t, engine, assetSource, List.of(), AssetCategory.PROP_SMALL));
            }
        }
        sceneManager = new SceneManager(eventBus, engine, sceneLoaders, deepSpaceLoader, 3);
        sceneStreamingSystem = new SceneStreamingSystem(sceneManager);
        engine.addSystem(sceneStreamingSystem);
        // Boot into the deep-space scene so a primary scene always exists.
        sceneManager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
```

In `update(float delta)` (currently ~line 867), push the player position to the scene streaming system. Replace:
```java
    public void update(float delta) {
        if (assetManager != null) {
            if (camera != null) streamingSystem.setCameraPosition(camera.position);
            assetManager.update();
        }
        engine.update(delta);
```
with:
```java
    public void update(float delta) {
        if (assetManager != null) {
            if (camera != null) streamingSystem.setCameraPosition(camera.position);
            assetManager.update();
        }
        if (sceneStreamingSystem != null && camera != null) {
            sceneStreamingSystem.setPlayerPosition(camera.position);
        }
        engine.update(delta);
```

Add a getter alongside the other getters (e.g. near `getEngine()`):
```java
    public SceneManager getSceneManager() { return sceneManager; }
```

- [ ] **Step 7: Build the whole module to verify the wiring compiles and nothing regressed**

Run: `gradlew.bat :core:test`
Expected: BUILD SUCCESSFUL — all existing tests plus the 8 new scene test classes pass. (`SceneStreamingSystem` is registered as an Ashley system; the bootstrap `requestTransition` drives the deep-space scene to ACTIVE on the first few `engine.update` calls.)

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(scene): wire SceneManager and streaming system into GameWorld"
```

---

## Self-Review Notes (resolved)

- **Spec coverage:** taxonomy/components (Tasks 1,4) · SceneLoader per-type seam (Task 5) · SceneManager max-2–3 + facade (Task 9) · transition FSM with all phases (Tasks 6–8) · hybrid triggering: auto hysteresis (Tasks 2,10) + explicit `requestTransition` for land/dock/board (Tasks 9,10) · async `step(budgetMs)` (Tasks 5,6) · event-driven disguise + timeout (Task 7) · failure rollback / never-strand (Task 8) · concurrency + max-scene guards (Task 9) · GameWorld/StreamingSystem/EventBus integration (Task 10) · headless tests, no GL (all tasks). Floating-origin interplay: scene anchors are double-precision (`Scene.galaxyAnchor`); the existing per-frame `CoordinateManager.checkRebase` in `GameWorld.update` handles rebasing — no new rebase code is introduced (deliberate, to avoid coupling), matching spec §6.
- **Type consistency:** `step` returns `float` everywhere; `SceneLoader` four methods identical across `EmptySceneLoader`/`DeepSpaceLoader`/`FakeSceneLoader`; `SceneManager` constructor arity matches all call sites (tests + GameWorld); `requestTransition` returns `boolean` consistently; event field names match between DTOs and assertions.
- **Placeholders:** none — every step contains full code or an exact edit.

## Deferred (out of scope for Sub-project A)

- Bespoke procgen-backed loaders: `OrbitalLoader` (`StarSystemGenerator`), `PlanetSurfaceLoader` (`PlanetTerrainSystem`/`WorldPopulator`), `StationInteriorLoader` (`SpaceStationGenerator`), `ShipInteriorLoader` (`ShipInteriorPhysicsSystem`/`ShipInteriorComponent`). Each replaces an `EmptySceneLoader` registry entry with no engine changes.
- Sub-project B — LOD bands (geometry/physics/AI) with hysteresis for planets/ships/NPCs.
- Sub-project C — client↔server zone alignment with `ZoneDefinition`/`ZoneHandoffManager`.
- Player repositioning specifics on landing/boarding (owned by the existing dock/land/interaction initiators that post `SceneTransitionRequest`).
- Queued transition backlog (single in-flight only — YAGNI).
