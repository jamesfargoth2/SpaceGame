# Hydrodynamics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate water physics (buoyancy, drag, waves, flooding, ballast) into Galactic Odyssey by curating eval-generated code, removing duplicates, and wiring into GameWorld.

**Architecture:** All water physics in `water/`, ship-specific flooding + HUD in `ship/flooding/`. Systems registered in GameWorld at priorities 10-17 (water physics) and 18-50 (ship integration). Ocean entities spawned dynamically when player enters ocean biome.

**Tech Stack:** Ashley ECS, gdx-bullet (Bullet physics), libGDX Scene2D (HUD), libGDX Pools (temp vectors)

**Spec:** `docs/superpowers/specs/2026-05-26-hydrodynamics-design.md`

**Skill:** Use `libgdx-hydrodynamics` skill for all water physics implementation guidance.

---

### Task 1: Delete Duplicate and Inferior Files

Remove eval-generated duplicates. These are files where a better implementation exists and keeping both would cause conflicts.

**Files to delete:**

- [ ] **Step 1: Delete duplicate buoyancy systems**

```
core/src/main/java/com/galacticodyssey/water/systems/VesselBuoyancySystem.java
core/src/main/java/com/galacticodyssey/water/systems/SubmarineBuoyancySystem.java
```

Reason: `BoatBuoyancySystem` handles all vessels with per-point hull-normal buoyancy. `VesselBuoyancySystem` is identical logic. `SubmarineBuoyancySystem` uses inferior height-fraction approximation.

- [ ] **Step 2: Delete redundant wave and control systems**

```
core/src/main/java/com/galacticodyssey/water/systems/SubmarineWaveSystem.java
core/src/main/java/com/galacticodyssey/water/systems/BallastControlSystem.java
core/src/main/java/com/galacticodyssey/water/systems/DepthControlSystem.java
```

Reason: `WaveSystem` handles all wave queries. `BallastSystem` already contains PID depth control — `BallastControlSystem` and `DepthControlSystem` split that logic unnecessarily.

- [ ] **Step 3: Delete inferior drag system and its component**

```
core/src/main/java/com/galacticodyssey/water/systems/WaterDragSystem.java
core/src/main/java/com/galacticodyssey/water/components/WaterDragComponent.java
```

Reason: `HydrodynamicDragSystem` separates skin friction, form drag, and wave-making drag. `WaterDragSystem` combines them and has no wave-making drag.

- [ ] **Step 4: Delete ship/flooding/Compartment.java (will use water/ version after merge)**

```
core/src/main/java/com/galacticodyssey/ship/flooding/Compartment.java
```

- [ ] **Step 5: Delete duplicate events in ship/flooding/events/**

Delete the entire `ship/flooding/events/` directory. All events will be consolidated into `water/events/`.

```
core/src/main/java/com/galacticodyssey/ship/flooding/events/FloodingStartedEvent.java
core/src/main/java/com/galacticodyssey/ship/flooding/events/StabilityWarningEvent.java
core/src/main/java/com/galacticodyssey/ship/flooding/events/CapsizeEvent.java
core/src/main/java/com/galacticodyssey/ship/flooding/events/HullBreachEvent.java
core/src/main/java/com/galacticodyssey/ship/flooding/events/CompartmentFloodedEvent.java
core/src/main/java/com/galacticodyssey/ship/flooding/events/BreachSealedEvent.java
```

- [ ] **Step 6: Commit deletions**

```
git add -u
git commit -m "refactor(water): remove duplicate eval-generated systems and events"
```

---

### Task 2: Merge Compartment — Add sealed Flag and waterHead Method

The `water/Compartment.java` is missing two features from the deleted `ship/flooding/Compartment.java`. Merge them in.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/water/Compartment.java`

- [ ] **Step 1: Add sealed field and waterHead method to water/Compartment.java**

Add after the `centroid` field (line 32):

```java
public boolean sealed;
```

Add after the existing `fillFraction()` method:

```java
public float fillFraction() {
    if (volume <= 0f) return 0f;
    return Math.max(0f, Math.min(1f, waterVolume / volume));
}

public float waterHead(float compartmentHeight) {
    return fillFraction() * compartmentHeight;
}
```

If `fillFraction()` already exists but doesn't clamp, replace it with the clamped version above.

- [ ] **Step 2: Add constructor**

```java
public Compartment(String id, float volume) {
    this.id = id;
    this.volume = volume;
}
```

Also add a no-arg constructor if one doesn't exist (Ashley components may need it):

```java
public Compartment() {}
```

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/water/Compartment.java
git commit -m "feat(water): merge sealed flag and waterHead into Compartment"
```

---

### Task 3: Upgrade Events in water/events/

The ship/flooding events were more detailed (severity levels, roll angle, fromBreach flag). Upgrade the water/events/ versions to match, and add BreachSealedEvent which only existed in ship/flooding.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/water/events/StabilityWarningEvent.java`
- Modify: `core/src/main/java/com/galacticodyssey/water/events/CapsizeEvent.java`
- Modify: `core/src/main/java/com/galacticodyssey/water/events/FloodingStartedEvent.java`
- Modify: `core/src/main/java/com/galacticodyssey/water/events/HullBreachEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/BreachSealedEvent.java`

- [ ] **Step 1: Upgrade StabilityWarningEvent to include severity and roll**

```java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class StabilityWarningEvent {
    public final Entity entity;
    public final float freeSurfaceGzLoss;
    public final float currentRollDeg;
    public final int severity; // 0=caution, 1=warning, 2=critical

    public StabilityWarningEvent(Entity entity, float freeSurfaceGzLoss,
                                  float currentRollDeg, int severity) {
        this.entity = entity;
        this.freeSurfaceGzLoss = freeSurfaceGzLoss;
        this.currentRollDeg = currentRollDeg;
        this.severity = severity;
    }
}
```

- [ ] **Step 2: Upgrade CapsizeEvent to include roll and flooded mass**

```java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class CapsizeEvent {
    public final Entity entity;
    public final float currentRollDeg;
    public final float totalFloodedMass;

    public CapsizeEvent(Entity entity, float currentRollDeg, float totalFloodedMass) {
        this.entity = entity;
        this.currentRollDeg = currentRollDeg;
        this.totalFloodedMass = totalFloodedMass;
    }
}
```

- [ ] **Step 3: Upgrade FloodingStartedEvent to include fromBreach flag**

```java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class FloodingStartedEvent {
    public final Entity entity;
    public final String compartmentId;
    public final boolean fromBreach;

    public FloodingStartedEvent(Entity entity, String compartmentId, boolean fromBreach) {
        this.entity = entity;
        this.compartmentId = compartmentId;
        this.fromBreach = fromBreach;
    }
}
```

- [ ] **Step 4: Upgrade HullBreachEvent to include compartment details**

```java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class HullBreachEvent {
    public final Entity entity;
    public final String compartmentId;
    public final float breachArea;
    public final float breachDepth;

    public HullBreachEvent(Entity entity, String compartmentId,
                           float breachArea, float breachDepth) {
        this.entity = entity;
        this.compartmentId = compartmentId;
        this.breachArea = breachArea;
        this.breachDepth = breachDepth;
    }
}
```

- [ ] **Step 5: Create BreachSealedEvent**

```java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class BreachSealedEvent {
    public final Entity entity;
    public final String compartmentId;

    public BreachSealedEvent(Entity entity, String compartmentId) {
        this.entity = entity;
        this.compartmentId = compartmentId;
    }
}
```

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/water/events/
git commit -m "feat(water): upgrade events with severity, roll, breach details; add BreachSealedEvent"
```

---

### Task 4: Rename ship/flooding Components and Systems

Rename `FloodingComponent` → `ShipFloodingComponent` and `FloodingSystem` → `ShipFloodingSystem` to avoid naming collision with `water/FloodingComponent` and `water/systems/FloodingSystem`.

**Files:**
- Rename + modify: `ship/flooding/components/FloodingComponent.java` → `ShipFloodingComponent.java`
- Rename + modify: `ship/flooding/systems/FloodingSystem.java` → `ShipFloodingSystem.java`
- Modify: `ship/flooding/systems/FloodingHudSystem.java` (update imports)
- Modify: `ship/flooding/FloodableShipFactory.java` (update imports)

- [ ] **Step 1: Create ShipFloodingComponent.java**

Create `core/src/main/java/com/galacticodyssey/ship/flooding/components/ShipFloodingComponent.java` with the same content as `FloodingComponent.java` but:
- Class name: `ShipFloodingComponent`
- Import `Compartment` from `com.galacticodyssey.water.Compartment` (not local)
- Import `DoorwayConnection` from `com.galacticodyssey.ship.flooding.DoorwayConnection`

- [ ] **Step 2: Create ShipFloodingSystem.java**

Create `core/src/main/java/com/galacticodyssey/ship/flooding/systems/ShipFloodingSystem.java` with the same content as `FloodingSystem.java` but:
- Class name: `ShipFloodingSystem`
- Import `ShipFloodingComponent` instead of `FloodingComponent`
- Import all events from `com.galacticodyssey.water.events.*` instead of `ship.flooding.events.*`
- Import `Compartment` from `com.galacticodyssey.water.Compartment`
- Update ComponentMapper to use `ShipFloodingComponent.class`

- [ ] **Step 3: Update FloodingHudSystem.java imports**

In `core/src/main/java/com/galacticodyssey/ship/flooding/systems/FloodingHudSystem.java`:
- Change `import ...ship.flooding.components.FloodingComponent` → `import ...ship.flooding.components.ShipFloodingComponent`
- Change all `import ...ship.flooding.events.*` → `import ...water.events.*`
- Change `import ...ship.flooding.Compartment` → `import ...water.Compartment`
- Update `ComponentMapper<FloodingComponent>` → `ComponentMapper<ShipFloodingComponent>`
- Update all `FloodingComponent` references in method bodies → `ShipFloodingComponent`

- [ ] **Step 4: Update FloodableShipFactory.java imports**

In `core/src/main/java/com/galacticodyssey/ship/flooding/FloodableShipFactory.java`:
- Change `import ...ship.flooding.components.FloodingComponent` → `import ...ship.flooding.components.ShipFloodingComponent`
- Change `import ...ship.flooding.Compartment` → `import ...water.Compartment` (if not already)
- Update return type `FloodingComponent` → `ShipFloodingComponent` in `attachFloodingLayout()`
- Update all `new FloodingComponent()` → `new ShipFloodingComponent()`
- Update all variable types `FloodingComponent` → `ShipFloodingComponent`

- [ ] **Step 5: Delete old files**

```
core/src/main/java/com/galacticodyssey/ship/flooding/components/FloodingComponent.java
core/src/main/java/com/galacticodyssey/ship/flooding/systems/FloodingSystem.java
```

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/flooding/
git commit -m "refactor(ship): rename FloodingComponent/System to ShipFlooding* to avoid collision with water/"
```

---

### Task 5: Fix water/ Systems to Query WaterBodyComponent Dynamically

The existing `BoatBuoyancySystem` receives `WaterBodyComponent` in its constructor. This won't work when oceans are spawned/despawned dynamically per planet. Systems need to look up the water body entity from the engine each frame.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/water/systems/BoatBuoyancySystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/water/systems/HydrodynamicDragSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/water/systems/FloodingSystem.java`

- [ ] **Step 1: Modify BoatBuoyancySystem to query water body from engine**

Remove `WaterBodyComponent waterBody` from the constructor. Add a helper that finds it:

```java
private WaterBodyComponent findWaterBody() {
    ImmutableArray<Entity> waterBodies = getEngine()
        .getEntitiesFor(Family.all(WaterBodyComponent.class).get());
    if (waterBodies.size() == 0) return null;
    return waterBodies.first().getComponent(WaterBodyComponent.class);
}
```

At the top of `processEntity()`, call `findWaterBody()` and early-return if null. Replace the stored `this.waterBody` field with the dynamic lookup.

Updated constructor:

```java
public BoatBuoyancySystem(CoordinateManager coordinateManager, WaveSystem waveSystem) {
    super(Family.all(HullComponent.class, PhysicsBodyComponent.class).get(), PRIORITY);
    this.coordinateManager = coordinateManager;
    this.waveSystem = waveSystem;
}
```

- [ ] **Step 2: Apply the same pattern to HydrodynamicDragSystem**

If it receives `WaterBodyComponent` in its constructor, change it to query dynamically using the same `findWaterBody()` helper pattern.

- [ ] **Step 3: Apply the same pattern to water/FloodingSystem**

If it reads fluid density from a constructor-injected water body, switch to dynamic lookup.

- [ ] **Step 4: Verify all water/ systems compile**

```
gradlew :core:compileJava
```

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/water/systems/
git commit -m "refactor(water): systems query WaterBodyComponent dynamically instead of constructor injection"
```

---

### Task 6: Create OceanSpawner

New class that spawns a `WaterBodyComponent` entity when the player enters an ocean biome on a planet.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/OceanSpawner.java`

- [ ] **Step 1: Write OceanSpawner**

```java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import java.util.Random;

public class OceanSpawner {

    private final Engine engine;
    private Entity oceanEntity;

    public OceanSpawner(Engine engine) {
        this.engine = engine;
    }

    public Entity spawnOcean(float baseHeight, float density, float viscosity,
                             long planetSeed, int waveCount) {
        if (oceanEntity != null) {
            despawnOcean();
        }

        oceanEntity = new Entity();
        WaterBodyComponent water = new WaterBodyComponent();
        water.baseHeight = baseHeight;
        water.density = density;
        water.kinematicViscosity = viscosity;

        Random rng = new Random(planetSeed);
        for (int i = 0; i < waveCount; i++) {
            WaveParams wave = new WaveParams();
            wave.amplitude = 0.3f + rng.nextFloat() * 1.5f;
            wave.wavelength = 20f + rng.nextFloat() * 80f;
            float g = 9.81f;
            wave.speed = (float) Math.sqrt(g * wave.wavelength / MathUtils.PI2);
            wave.steepness = 0.1f + rng.nextFloat() * 0.4f;
            wave.directionDeg = rng.nextFloat() * 360f;
            water.waves.add(wave);
        }

        oceanEntity.add(water);
        engine.addEntity(oceanEntity);
        return oceanEntity;
    }

    public Entity spawnSeawater(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 1025f, 1.19e-6f, planetSeed, 5);
    }

    public Entity spawnMethane(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 450f, 2.2e-7f, planetSeed, 3);
    }

    public Entity spawnLava(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 2700f, 1e3f, planetSeed, 2);
    }

    public Entity spawnAmmonia(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 680f, 3.5e-7f, planetSeed, 4);
    }

    public Entity spawnBrine(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 1200f, 1.5e-6f, planetSeed, 5);
    }

    public void despawnOcean() {
        if (oceanEntity != null) {
            engine.removeEntity(oceanEntity);
            oceanEntity = null;
        }
    }

    public boolean hasOcean() {
        return oceanEntity != null;
    }
}
```

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/water/OceanSpawner.java
git commit -m "feat(water): add OceanSpawner for dynamic ocean entity creation per planet type"
```

---

### Task 7: Register Water Systems in GameWorld

Wire all hydrodynamics systems into the engine with proper priority ordering and dependency injection.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add imports to GameWorld.java**

Add at the top of the file with the other imports:

```java
import com.galacticodyssey.water.OceanSpawner;
import com.galacticodyssey.water.systems.WaveSystem;
import com.galacticodyssey.water.systems.BoatBuoyancySystem;
import com.galacticodyssey.water.systems.HydrodynamicDragSystem;
import com.galacticodyssey.water.systems.BallastSystem;
import com.galacticodyssey.water.systems.FloodingSystem;
import com.galacticodyssey.water.systems.HullIntegritySystem;
import com.galacticodyssey.water.systems.BoatMotorSystem;
import com.galacticodyssey.water.systems.WakeTrailSystem;
import com.galacticodyssey.ship.flooding.systems.ShipFloodingSystem;
import com.galacticodyssey.ship.flooding.systems.FloodingHudSystem;
```

- [ ] **Step 2: Add fields for water systems that other code needs to reference**

Add fields alongside other system fields:

```java
private WaveSystem waveSystem;
private OceanSpawner oceanSpawner;
```

- [ ] **Step 3: Register water systems in the constructor**

Insert after the ship weapons system block (after line ~251), before VFX:

```java
// --- Water / Hydrodynamics ---
waveSystem = new WaveSystem(10);
engine.addSystem(waveSystem);
engine.addSystem(new BoatBuoyancySystem(coordinateManager, waveSystem));
engine.addSystem(new HydrodynamicDragSystem());
engine.addSystem(new BallastSystem(waveSystem, eventBus));
engine.addSystem(new FloodingSystem(eventBus));
engine.addSystem(new HullIntegritySystem(eventBus));
engine.addSystem(new BoatMotorSystem());
engine.addSystem(new WakeTrailSystem());
engine.addSystem(new ShipFloodingSystem(eventBus));

oceanSpawner = new OceanSpawner(engine);
```

Note: `FloodingHudSystem` needs a Scene2D Stage, which is available in `initializeSystems()` or when the game screen is created. Add it where the stage is accessible:

```java
// Add in the UI/HUD initialization section where stage is available
engine.addSystem(new FloodingHudSystem(eventBus));
```

- [ ] **Step 4: Add getter for OceanSpawner**

```java
public OceanSpawner getOceanSpawner() {
    return oceanSpawner;
}
```

- [ ] **Step 5: Verify build compiles**

```
gradlew :core:compileJava
```

Fix any import or constructor signature mismatches that surface.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(core): register hydrodynamics systems in GameWorld"
```

---

### Task 8: Fix Compilation Errors

The water/ systems may have been written referencing deleted systems, wrong component names, or old event imports. Do a compile pass and fix all errors.

**Files:**
- Various files under `water/systems/` and `ship/flooding/`

- [ ] **Step 1: Run full build**

```
gradlew :core:compileJava 2>&1 | head -100
```

- [ ] **Step 2: Fix each compilation error**

Common expected issues:
- Systems referencing deleted components (`WaterDragComponent`)
- Old event imports (`ship.flooding.events.*` → `water.events.*`)
- Constructor signature mismatches (removed `WaterBodyComponent` param)
- Compartment import path changes
- Event constructor argument count changes (upgraded events have more fields)

For each error: read the file, fix the import or reference, move on.

- [ ] **Step 3: Repeat until clean compile**

```
gradlew :core:compileJava
```

- [ ] **Step 4: Commit all fixes**

```
git add -A core/src/main/java/
git commit -m "fix(water): resolve compilation errors from refactor"
```

---

### Task 9: Verify Integration

Ensure the whole project builds and the water systems are registered correctly.

- [ ] **Step 1: Full project build**

```
gradlew build
```

- [ ] **Step 2: Verify system count**

Add a temporary log line in GameWorld after all systems are registered:

```java
Gdx.app.log("GameWorld", "Systems registered: " + engine.getSystems().size());
```

Run the desktop launcher and confirm the system count increased by the expected amount (10 new systems).

- [ ] **Step 3: Quick smoke test**

Launch the game, enter a planet with an ocean biome. Verify:
- No crash on ocean entry
- WaveSystem is ticking (add a debug log if needed)
- If a boat entity is spawned, it floats

- [ ] **Step 4: Remove debug logging and commit**

```
git add -A
git commit -m "feat(water): hydrodynamics system integration complete"
```
