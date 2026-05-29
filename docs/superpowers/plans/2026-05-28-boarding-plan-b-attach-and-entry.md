# Boarding Pipeline — Plan B: Attach (Clamp + Breaching Pod) & Hostile Interior Entry

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take a `VULNERABLE` ship (Plan A output) through the *attach* phase — by docking **clamp** *or* by **breaching pod** — to `BREACHED`, then transition the player into the enemy ship's interior (`INTERIOR_COMBAT`) and let them exit back to their own ship.

**Architecture:** Approach C from the design spec. The attach phase converges two paths (clamp / pod) on `BREACHED` + a ship-local `entryLocalPosition`, so entry is method-agnostic. `BoardingAttachSystem` reacts to `DockingCaptureEvent` (clamp) and ticks launched `BreachingPodComponent` entities (pod), writing `attachMethod`/`entryLocalPosition` and advancing the operation to `BREACHED` (publishing `ShipBreachedEvent`). `BoardingEntrySystem` reacts to `ShipBreachedEvent`, activates the target interior, moves the player into `ON_FOOT_INTERIOR` on the target ship, and advances to `INTERIOR_COMBAT`. `BoardingInitiationSystem` gives the piloting player an in-game trigger (Keys.G) to fire a breaching pod at a nearby disabled ship. Interior combat itself is Plan C.

**Tech Stack:** Java 17, libGDX, Ashley ECS, gdx-bullet, JUnit 5. Build: Gradle (`./gradlew core:test`). Build env: `JAVA_HOME=~/.jdks/temurin-25.0.3`. Run filtered tests with `--tests`.

**Spec:** [docs/superpowers/specs/2026-05-28-ship-boarding-pipeline-design.md](../specs/2026-05-28-ship-boarding-pipeline-design.md)
**Builds on:** [Plan A](2026-05-28-boarding-plan-a-disable-subsystems.md) (`BoardingOperationComponent`, `ShipSubsystemsComponent`, `BoardingOrchestratorSystem`, `ShipBoardableEvent`).

---

## Design decisions locked for this plan

- **Entry point is a ship-local `Vector3`** (`BoardingOperationComponent.entryLocalPosition`), mirroring `InteriorLayout.airlockPosition`. The `Entity entryPoint` field from Plan A stays unused (no marker entity is created). Entry world position = target ship transform applied to `entryLocalPosition`.
- **Clamp hostility gate:** the clamp bridge fires only when one of the two docked ships is already in `VULNERABLE` phase (i.e. someone disabled it). A disabled ship is the only thing that carries a `BoardingOperationComponent`, so friendly docking to a healthy ship never triggers a breach. No faction lookup required.
- **Interior combat runs in main-world coordinates (MVP):** the player and (in Plan C) defenders are positioned in world space at `targetShipPosition + roomLocalOffset`. The per-interior `btDiscreteDynamicsWorld` is activated for rendering/interior props; full interior-world physics co-location of boarders is a known limitation deferred to a later pass.
- **In-game trigger:** while `PILOTING`, pressing **G** near a `VULNERABLE` ship launches a breaching pod. Clamp boarding happens automatically if the player docks with a `VULNERABLE` ship via the existing docking gameplay.

---

## File Structure

New events in `com.galacticodyssey.ship.boarding.events`; new components in `com.galacticodyssey.ship.boarding`; new systems in `com.galacticodyssey.ship.boarding.systems`.

**Create:**
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/ShipBreachedEvent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/PlayerEnteredHostileInteriorEvent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/BreachingPodComponent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingAttachSystem.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingEntrySystem.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingInitiationSystem.java`

**Modify:**
- `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java` — add `entryLocalPosition` + snapshot fields.
- `core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java` — add entry-position fields.
- `core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java` — register Plan A + Plan B snapshots (IDs 75–77).
- `core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java` — add `boardPressed`.
- `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java` — bind Keys.G.
- `core/src/main/java/com/galacticodyssey/player/systems/InteractionSystem.java` — hostile-interior exit routing.
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — register the three new systems.

**Test:**
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingAttachClampTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingAttachPodTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingEntrySystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingInitiationSystemTest.java`
- `core/src/test/java/com/galacticodyssey/player/systems/HostileInteriorExitTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/AttachEntryIntegrationTest.java`

---

## Task 1: Attach/entry events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/ShipBreachedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/PlayerEnteredHostileInteriorEvent.java`

- [ ] **Step 1: Write `ShipBreachedEvent`**

```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;

/** A boarding attach completed: a hard connection (clamp or breach pod) opened an entry point. */
public final class ShipBreachedEvent {
    public final Entity aggressor;
    public final Entity target;
    public final AttachMethod method;
    /** Entry spawn point in TARGET-ship-local coordinates. */
    public final Vector3 entryLocalPosition;

    public ShipBreachedEvent(Entity aggressor, Entity target, AttachMethod method, Vector3 entryLocalPosition) {
        this.aggressor = aggressor;
        this.target = target;
        this.method = method;
        this.entryLocalPosition = new Vector3(entryLocalPosition);
    }
}
```

- [ ] **Step 2: Write `PlayerEnteredHostileInteriorEvent`**

```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** The player has transitioned into a hostile ship's interior to begin boarding combat. */
public final class PlayerEnteredHostileInteriorEvent {
    public final Entity player;
    public final Entity targetShip;

    public PlayerEnteredHostileInteriorEvent(Entity player, Entity targetShip) {
        this.player = player;
        this.targetShip = targetShip;
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/events/ShipBreachedEvent.java core/src/main/java/com/galacticodyssey/ship/boarding/events/PlayerEnteredHostileInteriorEvent.java
git commit -m "feat(boarding): add ShipBreached + PlayerEnteredHostileInterior events"
```

---

## Task 2: Extend `BoardingOperationComponent` with entry position + fix snapshot persistence

The operation needs to carry where boarders spawn. We add a ship-local `entryLocalPosition`, extend its snapshot, and register the boarding snapshots with Kryo (Plan A registered them only in `SnapshotComponentRegistry`, not `KryoRegistrar`, so they currently can't be serialized to a save file).

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java`

- [ ] **Step 1: Add the field + snapshot wiring to `BoardingOperationComponent`**

Add the import at the top (with the other imports):

```java
import com.badlogic.gdx.math.Vector3;
```

Add the field (next to `public Entity entryPoint;`):

```java
    /** Boarder spawn point in TARGET-ship-local coordinates (set at BREACHED). */
    public final Vector3 entryLocalPosition = new Vector3();
```

In `takeSnapshot()`, before `return s;`, add:

```java
        s.entryLocalX = entryLocalPosition.x;
        s.entryLocalY = entryLocalPosition.y;
        s.entryLocalZ = entryLocalPosition.z;
```

In `restoreFromSnapshot(...)`, before the trailing comment, add:

```java
        entryLocalPosition.set(s.entryLocalX, s.entryLocalY, s.entryLocalZ);
```

- [ ] **Step 2: Add the snapshot fields**

In `core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java`, add these fields (after `entryPointId`):

```java
    public float entryLocalX;
    public float entryLocalY;
    public float entryLocalZ;
```

- [ ] **Step 3: Register the boarding snapshots with Kryo**

In `KryoRegistrar.register(...)`, in the `// --- Ship subsystem snapshots ---` block (after the `DockingStateSnapshot.class, 74` line), add:

```java
        kryo.register(ShipSubsystemsSnapshot.class, 75);
        kryo.register(ShipSubsystemsSnapshot.Entry.class, 76);
        kryo.register(BoardingOperationSnapshot.class, 77);
```

> The `com.galacticodyssey.persistence.snapshots.*` wildcard import already covers these classes.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java
git commit -m "feat(boarding): add entryLocalPosition + register boarding snapshots with Kryo"
```

---

## Task 3: `BreachingPodComponent`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/BreachingPodComponent.java`

- [ ] **Step 1: Write the component**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

/**
 * A breaching pod in flight from an aggressor ship toward a target ship. It travels linearly
 * over {@link #flightDuration} seconds, then impacts — marking a hull breach and opening a
 * boarding entry point. Lives on a dedicated pod entity created by {@link
 * com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem}.
 */
public class BreachingPodComponent implements Component {
    public Entity aggressor;
    public Entity target;
    /** World-space start position. */
    public final Vector3 origin = new Vector3();
    /** World-space impact target (the target ship's hull surface estimate). */
    public final Vector3 impactPoint = new Vector3();
    /** Total flight time in seconds. */
    public float flightDuration = 1.5f;
    /** Elapsed flight time in seconds. */
    public float elapsed;
    public boolean impacted;
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/BreachingPodComponent.java
git commit -m "feat(boarding): add BreachingPodComponent for in-flight breach pods"
```

---

## Task 4: `BoardingAttachSystem` — docking-clamp bridge

The system listens for `DockingCaptureEvent(portA, portB)`. Because `DockingPortComponent` lives on the ship entity, both args are ship entities. If exactly one side is in `VULNERABLE` phase, that side is the target and the other is the aggressor: set `attachMethod = CLAMP`, `entryLocalPosition = target airlock`, advance to `BREACHED`, publish `ShipBreachedEvent`.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingAttachSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingAttachClampTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.components.ShipEntryPointComponent;
import com.galacticodyssey.ship.docking.events.DockingCaptureEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingAttachClampTest {

    private EventBus eventBus;
    private Engine engine;
    private final List<ShipBreachedEvent> breaches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingAttachSystem(eventBus));
        eventBus.subscribe(ShipBreachedEvent.class, breaches::add);
    }

    private Entity ship(boolean vulnerable) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        ShipEntryPointComponent entry = new ShipEntryPointComponent();
        entry.interiorPosition.set(1f, 2f, 3f);
        e.add(entry);
        if (vulnerable) {
            BoardingOperationComponent op = new BoardingOperationComponent();
            op.targetShip = e;
            op.phase = BoardingPhase.VULNERABLE;
            e.add(op);
        }
        engine.addEntity(e);
        return e;
    }

    @Test
    void clampOnVulnerableTargetBreachesViaAirlock() {
        Entity aggressor = ship(false);
        Entity target = ship(true);

        eventBus.publish(new DockingCaptureEvent(aggressor, target));
        engine.update(0.016f);

        BoardingOperationComponent op = target.getComponent(BoardingOperationComponent.class);
        assertEquals(BoardingPhase.BREACHED, op.phase);
        assertEquals(AttachMethod.CLAMP, op.attachMethod);
        assertEquals(new Vector3(1f, 2f, 3f), op.entryLocalPosition);
        assertSame(aggressor, op.aggressorShip);
        assertEquals(1, breaches.size());
        assertSame(target, breaches.get(0).target);
    }

    @Test
    void clampBetweenTwoHealthyShipsDoesNothing() {
        Entity a = ship(false);
        Entity b = ship(false);
        eventBus.publish(new DockingCaptureEvent(a, b));
        engine.update(0.016f);
        assertTrue(breaches.isEmpty());
        assertNull(a.getComponent(BoardingOperationComponent.class));
        assertNull(b.getComponent(BoardingOperationComponent.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingAttachClampTest`
Expected: FAIL — `BoardingAttachSystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.BreachingPodComponent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.components.ShipEntryPointComponent;
import com.galacticodyssey.ship.docking.events.DockingCaptureEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Drives the boarding ATTACH phase. Two converging paths reach {@code BREACHED} + a ship-local
 * {@code entryLocalPosition}:
 * <ul>
 *   <li><b>Clamp</b> — reacts to {@link DockingCaptureEvent}; if one docked ship is VULNERABLE,
 *       bridges it to BREACHED via the target's airlock.</li>
 *   <li><b>Breach pod</b> — ticks launched {@link BreachingPodComponent} entities; on impact marks
 *       a hull breach and opens an entry point at the impact.</li>
 * </ul>
 */
public class BoardingAttachSystem extends EntitySystem {

    public static final int PRIORITY = 8;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<ShipEntryPointComponent> ENTRY_M =
        ComponentMapper.getFor(ShipEntryPointComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BreachingPodComponent> POD_M =
        ComponentMapper.getFor(BreachingPodComponent.class);

    private final EventBus eventBus;
    private final Queue<DockingCaptureEvent> pendingDocks = new ArrayDeque<>();
    private ImmutableArray<Entity> pods;
    private final List<Entity> finishedPods = new ArrayList<>();
    private final Vector3 tmp = new Vector3();

    public BoardingAttachSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(DockingCaptureEvent.class, pendingDocks::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        pods = engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        pods = null;
    }

    @Override
    public void update(float deltaTime) {
        DockingCaptureEvent dock;
        while ((dock = pendingDocks.poll()) != null) {
            handleClamp(dock.portA, dock.portB);
        }
        tickPods(deltaTime);
    }

    private void handleClamp(Entity shipA, Entity shipB) {
        Entity target = vulnerable(shipA) ? shipA : (vulnerable(shipB) ? shipB : null);
        if (target == null) return;
        Entity aggressor = (target == shipA) ? shipB : shipA;

        BoardingOperationComponent op = OP_M.get(target);
        if (op.phase != BoardingPhase.VULNERABLE) return;

        ShipEntryPointComponent entry = ENTRY_M.get(target);
        Vector3 entryLocal = (entry != null) ? entry.interiorPosition : Vector3.Zero;

        breach(op, aggressor, target, AttachMethod.CLAMP, entryLocal);
    }

    private boolean vulnerable(Entity ship) {
        BoardingOperationComponent op = OP_M.get(ship);
        return op != null && op.phase == BoardingPhase.VULNERABLE;
    }

    /** Marks the operation BREACHED and announces it. Shared by clamp + pod paths. */
    private void breach(BoardingOperationComponent op, Entity aggressor, Entity target,
                        AttachMethod method, Vector3 entryLocal) {
        op.aggressorShip = aggressor;
        op.attachMethod = method;
        op.entryLocalPosition.set(entryLocal);
        op.phase = BoardingPhase.BREACHED;
        eventBus.publish(new ShipBreachedEvent(aggressor, target, method, op.entryLocalPosition));
    }

    private void tickPods(float deltaTime) {
        if (pods == null) return;
        finishedPods.clear();
        for (int i = 0, n = pods.size(); i < n; i++) {
            Entity podEntity = pods.get(i);
            BreachingPodComponent pod = POD_M.get(podEntity);
            if (pod.impacted) { finishedPods.add(podEntity); continue; }

            pod.elapsed += deltaTime;
            float t = pod.flightDuration <= 0f ? 1f : Math.min(1f, pod.elapsed / pod.flightDuration);
            // Advance pod transform (visual); impact at t>=1.
            TransformComponent podTransform = TRANSFORM_M.get(podEntity);
            if (podTransform != null) {
                podTransform.position.set(pod.origin).lerp(pod.impactPoint, t);
            }
            if (t >= 1f) {
                impactPod(pod);
                pod.impacted = true;
                finishedPods.add(podEntity);
            }
        }
        for (int i = 0; i < finishedPods.size(); i++) {
            getEngine().removeEntity(finishedPods.get(i));
        }
    }

    private void impactPod(BreachingPodComponent pod) {
        Entity target = pod.target;
        if (target == null) return;
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.phase != BoardingPhase.VULNERABLE) return;

        // Entry point in ship-local space = impactPoint relative to ship origin.
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (targetTransform != null) {
            tmp.set(pod.impactPoint).sub(targetTransform.position);
        } else {
            tmp.set(pod.impactPoint);
        }
        breach(op, pod.aggressor, target, AttachMethod.BREACH_POD, tmp);
    }

    /**
     * Launches a breaching pod from {@code aggressor} toward {@code target}. The pod flies for
     * {@link BreachingPodComponent#flightDuration} seconds and then breaches the target.
     * Returns the created pod entity (already added to the engine), or {@code null} if the
     * target is not in a VULNERABLE boarding state.
     */
    public Entity launchPod(Entity aggressor, Entity target) {
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.phase != BoardingPhase.VULNERABLE) return null;

        TransformComponent aggressorT = TRANSFORM_M.get(aggressor);
        TransformComponent targetT = TRANSFORM_M.get(target);

        Entity podEntity = new Entity();
        BreachingPodComponent pod = new BreachingPodComponent();
        pod.aggressor = aggressor;
        pod.target = target;
        if (aggressorT != null) pod.origin.set(aggressorT.position);
        if (targetT != null) pod.impactPoint.set(targetT.position);
        podEntity.add(pod);

        TransformComponent podTransform = new TransformComponent();
        podTransform.position.set(pod.origin);
        podEntity.add(podTransform);

        op.aggressorShip = aggressor;
        op.phase = BoardingPhase.ATTACHING;

        getEngine().addEntity(podEntity);
        return podEntity;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingAttachClampTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingAttachSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingAttachClampTest.java
git commit -m "feat(boarding): BoardingAttachSystem clamp bridge on VULNERABLE target"
```

---

## Task 5: `BoardingAttachSystem` — breaching-pod flight + impact

Covers the pod path of the system written in Task 4 (`launchPod` + `tickPods`/`impactPod`). No new production code — this task adds the test proving pod flight, breach, and entry-point derivation.

**Files:**
- Modify: (none — verifies Task 4 code)
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingAttachPodTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingAttachPodTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;
    private final List<ShipBreachedEvent> breaches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        eventBus.subscribe(ShipBreachedEvent.class, breaches::add);
    }

    private Entity shipAt(float x, float y, float z, boolean vulnerable) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, y, z);
        e.add(t);
        if (vulnerable) {
            BoardingOperationComponent op = new BoardingOperationComponent();
            op.targetShip = e;
            op.phase = BoardingPhase.VULNERABLE;
            e.add(op);
        }
        engine.addEntity(e);
        return e;
    }

    @Test
    void launchPodRequiresVulnerableTarget() {
        Entity aggressor = shipAt(0, 0, 0, false);
        Entity healthy = shipAt(10, 0, 0, false);
        assertNull(attach.launchPod(aggressor, healthy), "cannot board a non-VULNERABLE ship");
    }

    @Test
    void podFliesThenBreachesAtImpactLocalPosition() {
        Entity aggressor = shipAt(0, 0, 0, false);
        Entity target = shipAt(20, 0, 0, true);

        Entity pod = attach.launchPod(aggressor, target);
        assertNotNull(pod);
        assertEquals(BoardingPhase.ATTACHING, target.getComponent(BoardingOperationComponent.class).phase);

        // flightDuration default 1.5s — step until past it.
        for (int i = 0; i < 120 && breaches.isEmpty(); i++) {
            engine.update(0.05f);
        }

        BoardingOperationComponent op = target.getComponent(BoardingOperationComponent.class);
        assertEquals(BoardingPhase.BREACHED, op.phase);
        assertEquals(AttachMethod.BREACH_POD, op.attachMethod);
        // impactPoint == target world position (20,0,0); local = impact - targetPos == (0,0,0).
        assertEquals(new Vector3(0, 0, 0), op.entryLocalPosition);
        assertEquals(1, breaches.size());
        assertEquals(0, engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(BreachingPodComponent.class).get()).size(),
            "pod consumed on impact");
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingAttachPodTest`
Expected: PASS (2 tests). (Production code already exists from Task 4.)

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/ship/boarding/BoardingAttachPodTest.java
git commit -m "test(boarding): breaching-pod flight + impact breach coverage"
```

---

## Task 6: `BoardingEntrySystem` — transition player into the hostile interior

Reacts to `ShipBreachedEvent`. When the aggressor is the player's ship, activate the target interior, move the player to `ON_FOOT_INTERIOR` on the target ship, teleport them to the entry world position, advance the operation to `INTERIOR_COMBAT`, and publish `PlayerEnteredHostileInteriorEvent`. The Bullet body move is isolated and null-guarded so the logic is headlessly testable.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingEntrySystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingEntrySystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingEntrySystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingEntrySystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity aggressorShip;
    private Entity targetShip;
    private final List<PlayerEnteredHostileInteriorEvent> entered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingEntrySystem(eventBus, null));

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        player.add(state);
        engine.addEntity(player);

        aggressorShip = new Entity();
        aggressorShip.add(new TransformComponent());
        engine.addEntity(aggressorShip);

        targetShip = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(100f, 0f, 0f);
        targetShip.add(tt);
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.active = false;
        targetShip.add(interior);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = targetShip;
        op.aggressorShip = aggressorShip;
        op.phase = BoardingPhase.BREACHED;
        op.playerIsAggressor = true;
        targetShip.add(op);
        engine.addEntity(targetShip);

        // Player pilots the aggressor.
        state.currentShip = aggressorShip;

        eventBus.subscribe(PlayerEnteredHostileInteriorEvent.class, entered::add);
    }

    @Test
    void breachWithPlayerAggressorMovesPlayerIntoTargetInterior() {
        eventBus.publish(new ShipBreachedEvent(aggressorShip, targetShip, AttachMethod.CLAMP,
            new Vector3(1f, 0f, 2f)));
        engine.update(0.016f);

        PlayerStateComponent state = player.getComponent(PlayerStateComponent.class);
        assertEquals(PlayerMode.ON_FOOT_INTERIOR, state.currentMode);
        assertSame(targetShip, state.currentShip);
        assertTrue(targetShip.getComponent(ShipInteriorComponent.class).active,
            "target interior activated");
        // Entry world pos = targetPos (100,0,0) + entryLocal (1,0,2).
        assertEquals(new Vector3(101f, 0f, 2f), player.getComponent(TransformComponent.class).position);
        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            targetShip.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(1, entered.size());
        assertSame(targetShip, entered.get(0).targetShip);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingEntrySystemTest`
Expected: FAIL — `BoardingEntrySystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Transitions the player into a freshly-breached hostile interior. Reacts to {@link
 * ShipBreachedEvent}: when the player's ship is the aggressor, activates the target interior,
 * switches the player to {@code ON_FOOT_INTERIOR} on the target, teleports them to the entry
 * point, and advances the operation to {@code INTERIOR_COMBAT}.
 *
 * <p>The optional {@code mainWorld} reference is used to detach the player's rigid body from the
 * exterior physics world on entry; it is null-safe so the transition logic is testable headless.
 */
public class BoardingEntrySystem extends EntitySystem {

    public static final int PRIORITY = 2;

    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ShipInteriorComponent> INTERIOR_M =
        ComponentMapper.getFor(ShipInteriorComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);

    private final EventBus eventBus;
    private final btDiscreteDynamicsWorld mainWorld; // nullable (tests)
    private final Queue<ShipBreachedEvent> pending = new ArrayDeque<>();
    private final Matrix4 shipMat = new Matrix4();
    private final Vector3 entryWorld = new Vector3();
    private ImmutableArray<Entity> players;

    public BoardingEntrySystem(EventBus eventBus, btDiscreteDynamicsWorld mainWorld) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.mainWorld = mainWorld;
        eventBus.subscribe(ShipBreachedEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
    }

    @Override
    public void update(float deltaTime) {
        ShipBreachedEvent event;
        while ((event = pending.poll()) != null) {
            BoardingOperationComponent op = OP_M.get(event.target);
            if (op == null || !op.playerIsAggressor) continue;
            enterInterior(event.target, op);
        }
    }

    private void enterInterior(Entity target, BoardingOperationComponent op) {
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PlayerStateComponent state = STATE_M.get(player);

        // Activate the target interior so its physics/render systems step.
        ShipInteriorComponent interior = INTERIOR_M.get(target);
        if (interior != null) interior.active = true;

        // Compute entry world position from the target transform + ship-local entry point.
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (targetTransform != null) {
            shipMat.set(targetTransform.position, targetTransform.rotation);
            entryWorld.set(op.entryLocalPosition).mul(shipMat);
        } else {
            entryWorld.set(op.entryLocalPosition);
        }

        detachFromMainWorld(player);
        teleport(player, entryWorld);

        state.currentMode = PlayerMode.ON_FOOT_INTERIOR;
        state.currentShip = target;

        op.phase = BoardingPhase.INTERIOR_COMBAT;
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
    }

    private void detachFromMainWorld(Entity player) {
        if (mainWorld == null) return;
        PhysicsBodyComponent physics = PHYSICS_M.get(player);
        if (physics != null && physics.body != null) {
            mainWorld.removeRigidBody(physics.body);
        }
    }

    private void teleport(Entity player, Vector3 position) {
        TransformComponent transform = TRANSFORM_M.get(player);
        if (transform != null) transform.position.set(position);
        PhysicsBodyComponent physics = PHYSICS_M.get(player);
        if (physics != null && physics.body != null) {
            Matrix4 bodyTransform = physics.body.getWorldTransform();
            bodyTransform.setTranslation(position);
            physics.body.setWorldTransform(bodyTransform);
            physics.body.setLinearVelocity(new Vector3(0, 0, 0));
            physics.body.setAngularVelocity(new Vector3(0, 0, 0));
            physics.body.activate();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingEntrySystemTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingEntrySystem.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingEntrySystemTest.java
git commit -m "feat(boarding): BoardingEntrySystem moves player into hostile interior"
```

---

## Task 7: Hostile-interior exit routing in `InteractionSystem`

When the player is inside a boarded hostile ship (`currentShip` has a `BoardingOperationComponent` whose `aggressorShip` is the player's home ship), pressing **F** at the airlock must return them to **PILOTING their own ship**, not dump them to `ON_FOOT_EXTERIOR`. We extract the decision into a pure static helper so it is unit-testable, then branch `checkShipExit` on it.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/InteractionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/systems/HostileInteriorExitTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostileInteriorExitTest {

    @Test
    void healthyOwnShipIsNotHostile() {
        Entity ownShip = new Entity();
        assertNull(InteractionSystem.boardingHomeShip(ownShip),
            "a ship with no boarding op is the player's own ship — normal exit");
    }

    @Test
    void boardedHostileShipReturnsAggressorHomeShip() {
        Entity homeShip = new Entity();
        Entity hostile = new Entity();
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = hostile;
        op.aggressorShip = homeShip;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        hostile.add(op);

        assertSame(homeShip, InteractionSystem.boardingHomeShip(hostile),
            "exiting a boarded hostile ship returns the player to their aggressor ship");
    }

    @Test
    void resolvedOperationIsNotHostile() {
        Entity hostile = new Entity();
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = hostile;
        op.aggressorShip = new Entity();
        op.phase = BoardingPhase.RESOLVED;
        hostile.add(op);
        assertNull(InteractionSystem.boardingHomeShip(hostile));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.player.systems.HostileInteriorExitTest`
Expected: FAIL — `boardingHomeShip` not defined.

- [ ] **Step 3: Add the helper + branch the exit**

Add this import near the other `com.galacticodyssey.ship...` imports in `InteractionSystem.java`:

```java
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
```

Add this static helper to the class body (e.g. just above `freezePlayerBody`):

```java
    /**
     * If {@code currentShip} is a ship the player has boarded as an aggressor (active boarding
     * op, not yet RESOLVED), returns the player's own (aggressor) ship to return to on exit;
     * otherwise null (the ship is the player's own — use the normal exit path).
     */
    public static Entity boardingHomeShip(Entity currentShip) {
        if (currentShip == null) return null;
        BoardingOperationComponent op = currentShip.getComponent(BoardingOperationComponent.class);
        if (op == null) return null;
        if (op.phase == BoardingOperationComponent.BoardingPhase.RESOLVED
                || op.phase == BoardingOperationComponent.BoardingPhase.NONE) return null;
        return op.aggressorShip;
    }
```

Replace the body of `checkShipExit` (the `if (input.interactPressed) { ... }` block) so that a hostile exit returns the player to piloting their home ship. The full method becomes:

```java
    private void checkShipExit(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (state.currentShip == null) return;
        ShipEntryPointComponent entry = entryMapper.get(state.currentShip);
        TransformComponent playerTransform = transformMapper.get(player);
        TransformComponent shipTransform = transformMapper.get(state.currentShip);
        if (entry == null) return;

        toWorldSpace(shipTransform, entry.interiorPosition, worldPos);
        float dist = tempVec.set(playerTransform.position).dst(worldPos);

        if (dist < entry.triggerRadius) {
            Entity homeShip = boardingHomeShip(state.currentShip);
            if (homeShip != null) {
                eventBus.publish(new InteractionPromptEvent("[F] Return to Your Ship", true));
                if (input.interactPressed) {
                    ShipInteriorComponent interior = interiorMapper.get(state.currentShip);
                    if (interior != null) interior.active = false;
                    state.currentMode = PlayerMode.PILOTING;
                    state.currentShip = homeShip;
                    TransformComponent homeTransform = transformMapper.get(homeShip);
                    if (homeTransform != null) {
                        worldPos.set(homeTransform.position);
                        teleportPlayer(player, worldPos);
                    }
                }
                return;
            }

            eventBus.publish(new InteractionPromptEvent("[F] Exit Ship", true));
            if (input.interactPressed) {
                ShipInteriorComponent interior = interiorMapper.get(state.currentShip);
                interior.active = false;
                Entity ship = state.currentShip;
                state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
                state.currentShip = null;
                eventBus.publish(new PlayerExitShipEvent(player, ship));

                teleportPlayer(player, worldPos);
            }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.player.systems.HostileInteriorExitTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/InteractionSystem.java core/src/test/java/com/galacticodyssey/player/systems/HostileInteriorExitTest.java
git commit -m "feat(boarding): exiting a boarded hostile ship returns player to piloting"
```

---

## Task 8: In-game pod trigger — `boardPressed` input + `BoardingInitiationSystem`

Gives the piloting player a way to start a breach-pod boarding: press **G** near a `VULNERABLE` ship. The decision logic (nearest VULNERABLE ship within range) is a pure helper for testability.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingInitiationSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingInitiationSystemTest.java`

- [ ] **Step 1: Add the input field**

In `PlayerInputComponent.java`, add after `interactPressed`:

```java
    public boolean boardPressed;
```

- [ ] **Step 2: Bind Keys.G in `PlayerInputSystem`**

In `PlayerInputSystem.java`, find the `keyDown(int keycode)` method. Add a field near the top of the input-adapter inner class alongside `private boolean interactPressed;`:

```java
        private boolean boardPressed;
```

Inside `keyDown`, alongside the existing `if (keycode == Input.Keys.F) { ... }` block, add:

```java
            if (keycode == Input.Keys.G) {
                boardPressed = true;
                return true;
            }
```

In the piloting branch where the code does `if (interactPressed) { ... input.interactPressed = true; interactPressed = false; }` (the block around line 284), add immediately after it:

```java
        if (boardPressed) {
            PlayerInputComponent input = inputMapper.get(player);
            if (input != null) input.boardPressed = true;
            boardPressed = false;
        }
```

> If `inputMapper`/`player` are named differently in that scope, mirror the exact pattern used by the adjacent `interactPressed` handling (use the same local variable the surrounding block uses to reach the `PlayerInputComponent`).

- [ ] **Step 3: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingInitiationSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingInitiationSystemTest {

    private Entity shipAt(Engine engine, float x, boolean vulnerable) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
        e.add(t);
        if (vulnerable) {
            BoardingOperationComponent op = new BoardingOperationComponent();
            op.targetShip = e;
            op.phase = BoardingPhase.VULNERABLE;
            e.add(op);
        }
        engine.addEntity(e);
        return e;
    }

    @Test
    void findsNearestVulnerableShipInRange() {
        Engine engine = new Engine();
        Entity self = shipAt(engine, 0, false);
        Entity near = shipAt(engine, 30, true);
        shipAt(engine, 500, true); // out of range

        Entity found = BoardingInitiationSystem.nearestBoardable(
            engine, self, new Vector3(0, 0, 0), 100f);
        assertSame(near, found);
    }

    @Test
    void ignoresHealthyAndSelf() {
        Engine engine = new Engine();
        Entity self = shipAt(engine, 0, true); // self is VULNERABLE but must be excluded
        shipAt(engine, 20, false);             // healthy
        assertNull(BoardingInitiationSystem.nearestBoardable(
            engine, self, new Vector3(0, 0, 0), 100f));
    }

    @Test
    void pressingBoardLaunchesPodAtNearbyVulnerableShip() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        BoardingAttachSystem attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);

        Entity self = shipAt(engine, 0, false);
        Entity target = shipAt(engine, 30, true);

        // Player piloting `self`, pressing G.
        Entity player = com.galacticodyssey.ship.boarding.BoardingTestSupport
            .pilotingPlayer(engine, self, /*boardPressed*/ true);

        BoardingInitiationSystem init = new BoardingInitiationSystem(eventBus, attach);
        engine.addSystem(init);
        engine.update(0.016f);

        assertEquals(1, engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(BreachingPodComponent.class).get()).size(),
            "G near a VULNERABLE ship launches a breaching pod");
        assertFalse(player.getComponent(
            com.galacticodyssey.player.components.PlayerInputComponent.class).boardPressed,
            "board input consumed");
    }
}
```

- [ ] **Step 3a: Add the tiny test-support helper**

Create `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingTestSupport.java`:

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;

/** Shared headless fixtures for boarding tests. */
public final class BoardingTestSupport {
    private BoardingTestSupport() {}

    /** A player entity piloting {@code ship}, with the given board-input state. */
    public static Entity pilotingPlayer(Engine engine, Entity ship, boolean boardPressed) {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        player.add(state);
        PlayerInputComponent input = new PlayerInputComponent();
        input.boardPressed = boardPressed;
        player.add(input);
        engine.addEntity(player);
        return player;
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingInitiationSystemTest`
Expected: FAIL — `BoardingInitiationSystem` does not exist.

- [ ] **Step 5: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;

/**
 * In-game initiation of a breach-pod boarding. While the player is PILOTING and a VULNERABLE
 * ship is within {@link #POD_RANGE}, pressing the board key (G → {@code boardPressed}) launches
 * a breaching pod via {@link BoardingAttachSystem#launchPod}.
 */
public class BoardingInitiationSystem extends EntitySystem {

    public static final int PRIORITY = 1;
    public static final float POD_RANGE = 150f;

    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<PlayerInputComponent> INPUT_M =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);

    private final EventBus eventBus;
    private final BoardingAttachSystem attachSystem;
    private ImmutableArray<Entity> players;

    public BoardingInitiationSystem(EventBus eventBus, BoardingAttachSystem attachSystem) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.attachSystem = attachSystem;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class, PlayerInputComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
    }

    @Override
    public void update(float deltaTime) {
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PlayerStateComponent state = STATE_M.get(player);
        PlayerInputComponent input = INPUT_M.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;
        if (!input.boardPressed) return;
        input.boardPressed = false;

        TransformComponent shipT = TRANSFORM_M.get(state.currentShip);
        Vector3 origin = (shipT != null) ? shipT.position : Vector3.Zero;
        Entity target = nearestBoardable(getEngine(), state.currentShip, origin, POD_RANGE);
        if (target != null) {
            attachSystem.launchPod(state.currentShip, target);
        }
    }

    /**
     * Returns the nearest ship (other than {@code self}) within {@code range} of {@code origin}
     * that is in the VULNERABLE boarding phase, or null.
     */
    public static Entity nearestBoardable(Engine engine, Entity self, Vector3 origin, float range) {
        ImmutableArray<Entity> ships = engine.getEntitiesFor(
            Family.all(BoardingOperationComponent.class, TransformComponent.class).get());
        Entity best = null;
        float bestDist2 = range * range;
        for (int i = 0, n = ships.size(); i < n; i++) {
            Entity ship = ships.get(i);
            if (ship == self) continue;
            if (OP_M.get(ship).phase != BoardingPhase.VULNERABLE) continue;
            float d2 = TRANSFORM_M.get(ship).position.dst2(origin);
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = ship;
            }
        }
        return best;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingInitiationSystemTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingInitiationSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingInitiationSystemTest.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingTestSupport.java
git commit -m "feat(boarding): G-key launches breaching pod at nearby disabled ship"
```

---

## Task 9: Register the new systems in `GameWorld`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add imports**

With the other boarding-system imports (near lines 212–214):

```java
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingEntrySystem;
import com.galacticodyssey.ship.boarding.systems.BoardingInitiationSystem;
```

- [ ] **Step 2: Register the systems**

In the constructor, immediately after the existing line `engine.addSystem(new ShipSubsystemSystem(eventBus));` (around line 468), add:

```java
        BoardingAttachSystem boardingAttachSystem = new BoardingAttachSystem(eventBus);
        engine.addSystem(boardingAttachSystem);
        engine.addSystem(new BoardingEntrySystem(eventBus, bulletPhysicsSystem.getDynamicsWorld()));
        engine.addSystem(new BoardingInitiationSystem(eventBus, boardingAttachSystem));
```

> `bulletPhysicsSystem` is the existing exterior-world physics system already constructed earlier in this constructor (its `getDynamicsWorld()` is used elsewhere, e.g. the `VehicleBayService` wiring around line 487).

- [ ] **Step 3: Verify the full suite builds and passes**

Run: `./gradlew core:test`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(boarding): register attach, entry, and initiation systems"
```

---

## Task 10: End-to-end integration test (VULNERABLE → pod → BREACHED → player inside)

Proves the Plan B slice: a VULNERABLE ship, a launched pod, and the full attach→entry chain leaving the player in the target's interior in `INTERIOR_COMBAT`.

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/AttachEntryIntegrationTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingEntrySystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttachEntryIntegrationTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        engine.addSystem(new BoardingEntrySystem(eventBus, null));
    }

    @Test
    void podBoardingPutsPlayerInsideTargetInterior() {
        // Aggressor (player ship) + player.
        Entity aggressor = new Entity();
        aggressor.add(new TransformComponent());
        engine.addEntity(aggressor);

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = aggressor;
        player.add(state);
        engine.addEntity(player);

        // VULNERABLE target with an interior + a marked player-aggressor operation.
        Entity target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(40f, 0f, 0f);
        target.add(tt);
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.active = false;
        target.add(interior);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.phase = BoardingPhase.VULNERABLE;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        attach.launchPod(aggressor, target);

        for (int i = 0; i < 200
            && target.getComponent(BoardingOperationComponent.class).phase != BoardingPhase.INTERIOR_COMBAT;
            i++) {
            engine.update(0.05f);
        }

        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(PlayerMode.ON_FOOT_INTERIOR, state.currentMode);
        assertSame(target, state.currentShip);
        assertTrue(target.getComponent(ShipInteriorComponent.class).active);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.AttachEntryIntegrationTest`
Expected: PASS.

- [ ] **Step 3: Run the whole suite**

Run: `./gradlew core:test`
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/ship/boarding/AttachEntryIntegrationTest.java
git commit -m "test(boarding): integration test for attach -> entry pipeline"
```

---

## Plan B complete

At this point a `VULNERABLE` ship can be attached to by docking clamp (automatic on hostile dock) or by a player-launched breaching pod (press **G** when piloting near a disabled ship). Either path advances the operation to `BREACHED`, and the player is moved into the target's interior (`ON_FOOT_INTERIOR`, `INTERIOR_COMBAT`), able to return to their own ship by pressing **F** at the airlock. Boarding state persists across save/load (Kryo registrations fixed).

**Hand-off interfaces locked for Plan C:**
- `PlayerEnteredHostileInteriorEvent(player, targetShip)` — Plan C's `BoardingCombatSystem` listens to spawn defenders.
- `BoardingOperationComponent.entryLocalPosition` + `phase == INTERIOR_COMBAT` — combat begins here.
- `BoardingAttachSystem.launchPod(aggressor, target)` — reused by Plan C's `EnemyBoardingAISystem` for NPC aggressors.
- `BoardingOperationComponent.playerIsAggressor` — routes player-boards-NPC vs NPC-boards-player.
