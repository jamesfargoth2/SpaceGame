# Vehicle Bay & Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the player store ground vehicles in a ship's vehicle bay, deploy one onto a planet surface via the ramp, walk up to it and drive it (with working weapons and damageable armor), then drive it back and retrieve it into the bay.

**Architecture:** Mirror the existing ship-pilot transition pattern. A data-driven `VehicleRegistry` (JSON) feeds a `VehicleFactory` that assembles a combat-ready ECS entity from existing components. A new `PlayerMode.DRIVING` gates a `VehicleControlSystem` (driver input → vehicle) and the existing `SurfaceVehicleSystem` (extended to consume throttle/steer input). A thin `VehicleWeaponSystem` publishes the same `WeaponFiredEvent` the player's `WeaponSystem` does, so the entire damage/projectile/hitscan/armor pipeline is reused. A `VehicleBayService` handles deploy/retrieve. Rendering and the Scene2D bay panel are GL-bound and wired into `GameScreen`/`GameWorld`.

**Tech Stack:** Java, libGDX 1.13, Ashley ECS, gdx-bullet (Bullet physics), Scene2D.UI, JUnit 5. Build: Gradle (`.\gradlew.bat`).

**Spec:** [docs/superpowers/specs/2026-05-28-vehicle-bay-deployment-design.md](../specs/2026-05-28-vehicle-bay-deployment-design.md)

---

## File structure

**New files:**
- `core/.../planet/terrain/VehicleTagComponent.java` — marks a vehicle entity, holds source definition id
- `core/.../planet/terrain/VehicleEntryPointComponent.java` — walk-in trigger radius + exit offset
- `core/.../planet/terrain/VehicleRenderComponent.java` — holds model path for the render hook (no GL)
- `core/.../ship/components/VehicleBayComponent.java` — bay storage on ships (Snapshotable)
- `core/.../data/VehicleDefinition.java` — data model (+ nested `VehicleWeaponStats`)
- `core/.../data/VehicleRegistry.java` — JSON loader, lookup by id
- `core/.../planet/terrain/VehicleFactory.java` — builds a vehicle entity
- `core/.../planet/terrain/VehicleControlSystem.java` — driver input → vehicle (DRIVING only)
- `core/.../planet/terrain/VehicleWeaponSystem.java` — thin fire system → `WeaponFiredEvent`
- `core/.../planet/terrain/VehicleBayService.java` — deploy/retrieve/capacity logic
- `core/.../planet/terrain/events/VehicleDeployedEvent.java`, `VehicleRetrievedEvent.java`
- `core/.../core/events/PlayerEnterVehicleEvent.java`, `PlayerExitVehicleEvent.java`
- `core/.../planet/terrain/VehicleCameraSystem.java` — chase cam (DRIVING only)
- `core/.../ui/VehicleBayPanel.java` — Scene2D bay UI
- `core/.../persistence/snapshots/VehicleBaySnapshot.java` — save/load
- `core/src/main/resources/data/vehicles/vehicles.json` — starter vehicle definitions
- Test files under `core/src/test/java/...` mirroring each headless class

**Modified files:**
- `core/.../ship/RoomType.java` — add `VEHICLE_BAY`
- `core/.../planet/terrain/GroundVehicleComponent.java` — add `throttleInput`, `steerInput`
- `core/.../planet/terrain/SurfaceVehicleSystem.java` — consume throttle/steer
- `core/.../player/components/PlayerStateComponent.java` — add `DRIVING`, `currentVehicle`
- `core/.../player/systems/PlayerMovementSystem.java` — guard off during DRIVING
- `core/.../player/systems/CameraSystem.java` — guard off during DRIVING
- `core/.../player/systems/InteractionSystem.java` — enter/exit vehicle + retrieve
- `core/.../core/GameWorld.java` — register new systems + load registry
- `core/.../ui/GameScreen.java` — render deployed vehicles
- `core/.../persistence/SnapshotComponentRegistry.java` — register bay snapshot

---

## Conventions for every task

- Package root: `com.galacticodyssey`. The full path for `data/Foo.java` is `core/src/main/java/com/galacticodyssey/data/Foo.java`.
- Run a single test class: `.\gradlew.bat :core:test --tests "com.galacticodyssey.<pkg>.<Class>"`
- Tests that touch Bullet rigid bodies must call `Bullet.init()` once in a `@BeforeAll` (see existing `ShipFlightSystemTest`).
- Commit after each task with the message shown in its final step.

---

## Phase 1 — Data & components (headless)

### Task 1: Add `VEHICLE_BAY` room type

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/RoomType.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/RoomTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomTypeTest {
    @Test
    void vehicleBayExistsWithValidBounds() {
        RoomType bay = RoomType.valueOf("VEHICLE_BAY");
        assertTrue(bay.maxSizeX >= bay.minSizeX);
        assertTrue(bay.maxSizeZ >= bay.minSizeZ);
        assertTrue(bay.maxSizeY >= bay.minSizeY);
        assertNotNull(bay.floorColor);
        assertNotNull(bay.accentColor);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.RoomTypeTest"`
Expected: FAIL — `No enum constant ... VEHICLE_BAY`.

- [ ] **Step 3: Add the enum constant**

In `RoomType.java`, add after the `ARMORY(...)` entry (replace the `;` that terminates `ARMORY` with a `,` and append):

```java
    ARMORY(2, 2, 2, 2, 2, 2,
        new Color(0.25f, 0.25f, 0.25f, 1f), new Color(0.7f, 0.2f, 0.2f, 1f)),
    VEHICLE_BAY(4, 4, 2, 7, 6, 3,
        new Color(0.22f, 0.24f, 0.26f, 1f), new Color(0.5f, 0.6f, 0.8f, 1f));
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.RoomTypeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/RoomType.java core/src/test/java/com/galacticodyssey/ship/RoomTypeTest.java
git commit -m "feat(ship): add VEHICLE_BAY room type"
```

---

### Task 2: New vehicle components + `GroundVehicleComponent` input fields

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleTagComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleEntryPointComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleRenderComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/GroundVehicleComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/VehicleComponentsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleComponentsTest {
    @Test
    void tagHoldsDefinitionId() {
        VehicleTagComponent tag = new VehicleTagComponent();
        tag.definitionId = "rover_light";
        assertEquals("rover_light", tag.definitionId);
    }

    @Test
    void entryPointHasDefaultRadius() {
        VehicleEntryPointComponent entry = new VehicleEntryPointComponent();
        assertTrue(entry.triggerRadius > 0f);
        assertNotNull(entry.localExitOffset);
    }

    @Test
    void groundVehicleHasInputFieldsDefaultingToZero() {
        GroundVehicleComponent v = new GroundVehicleComponent();
        assertEquals(0f, v.throttleInput);
        assertEquals(0f, v.steerInput);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleComponentsTest"`
Expected: FAIL — classes/fields not found.

- [ ] **Step 3: Create the components and add fields**

`VehicleTagComponent.java`:
```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Component;

/** Marks an entity as a deployed ground vehicle and records its source definition id. */
public class VehicleTagComponent implements Component {
    public String definitionId;
}
```

`VehicleEntryPointComponent.java`:
```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** Walk-in trigger for entering the driver seat, plus where the player is dropped on exit. */
public class VehicleEntryPointComponent implements Component {
    public float triggerRadius = 2.5f;
    /** Where the player appears (vehicle-local) when exiting the vehicle. */
    public final Vector3 localExitOffset = new Vector3(2f, 0f, 0f);
}
```

`VehicleRenderComponent.java`:
```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Component;

/**
 * Holds the model asset path for a vehicle so the GL render layer can lazily build a
 * ModelInstance. Kept free of GL types so factories/systems stay headless-testable.
 */
public class VehicleRenderComponent implements Component {
    public String modelPath;
    /** Set by the render hook once the ModelInstance has been created. */
    public boolean instanceCreated;
}
```

In `GroundVehicleComponent.java`, add two fields after `sinkageDepth`:
```java
    public float sinkageDepth;
    /** Driver throttle, -1 (full reverse) .. +1 (full forward). */
    public float throttleInput;
    /** Driver steering, -1 (left) .. +1 (right). */
    public float steerInput;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleComponentsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/VehicleTagComponent.java core/src/main/java/com/galacticodyssey/planet/terrain/VehicleEntryPointComponent.java core/src/main/java/com/galacticodyssey/planet/terrain/VehicleRenderComponent.java core/src/main/java/com/galacticodyssey/planet/terrain/GroundVehicleComponent.java core/src/test/java/com/galacticodyssey/planet/terrain/VehicleComponentsTest.java
git commit -m "feat(vehicle): add vehicle tag/entry/render components and driver input fields"
```

---

### Task 3: `VehicleDefinition` + nested weapon stats

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/VehicleDefinition.java`
- Test: `core/src/test/java/com/galacticodyssey/data/VehicleDefinitionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleDefinitionTest {
    @Test
    void holdsPhysicsSurvivalAndWeaponFields() {
        VehicleDefinition def = new VehicleDefinition();
        def.id = "rover_light";
        def.maxHP = 250f;
        def.armorValue = 10f;
        def.baySlots = 1;
        def.weapon = new VehicleDefinition.VehicleWeaponStats();
        def.weapon.damage = 30f;
        def.weapon.fireRate = 4f;
        def.weapon.hitscan = true;
        def.weapon.damageType = DamageType.BALLISTIC;
        def.weapon.magSize = 60;

        assertEquals("rover_light", def.id);
        assertEquals(250f, def.maxHP);
        assertEquals(1, def.baySlots);
        assertEquals(DamageType.BALLISTIC, def.weapon.damageType);
        assertEquals(60, def.weapon.magSize);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.data.VehicleDefinitionTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Create the class**

`VehicleDefinition.java`:
```java
package com.galacticodyssey.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;

/** Data-driven definition for a ground vehicle. Loaded from data/vehicles/*.json. */
public class VehicleDefinition {
    // Identity & rendering
    public String id;
    public String displayName;
    public String modelPath;
    public String sizeClass;

    // Physics (feeds GroundVehicleComponent + the rigid body)
    public float mass = 1200f;
    public float wheelbase = 3f;
    public float trackWidth = 2f;
    public float groundClearance = 0.4f;
    public float maxDriveForce = 8000f;
    public float maxSteerAngle = 35f;
    public float anchorBreakForce = 50000f;
    public float dynamicLift = 0f;

    // Survivability
    public float maxHP = 200f;
    public float armorValue = 0f;

    // Bay footprint
    public int baySlots = 1;

    // Inline weapon (populates a RangedWeaponComponent)
    public VehicleWeaponStats weapon;

    public static class VehicleWeaponStats {
        public float damage = 20f;
        public float fireRate = 3f;
        public float range = 120f;
        public boolean hitscan = true;
        public float projectileSpeed = 200f;
        public DamageType damageType = DamageType.BALLISTIC;
        public FiringMode firingMode = FiringMode.AUTO;
        public int magSize = 50;
        public float reloadTime = 2.5f;
        public float spread = 1.5f;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.data.VehicleDefinitionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/VehicleDefinition.java core/src/test/java/com/galacticodyssey/data/VehicleDefinitionTest.java
git commit -m "feat(vehicle): add VehicleDefinition data model with inline weapon stats"
```

---

### Task 4: `VehicleRegistry` + starter JSON

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/VehicleRegistry.java`
- Create: `core/src/main/resources/data/vehicles/vehicles.json`
- Test: `core/src/test/java/com/galacticodyssey/data/VehicleRegistryTest.java`

The registry parses JSON with libGDX `JsonReader`/`JsonValue` (same approach as `ShipWeaponRegistry`). To keep the loader unit-testable without `Gdx.files`, expose a `loadFromJson(String json)` method that the `Gdx.files`-based `load(String path)` delegates to.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleRegistryTest {
    private static final String JSON = "{ \"vehicles\": [\n" +
        "  { \"id\": \"rover_light\", \"displayName\": \"Light Rover\", \"modelPath\": \"models/rover.g3db\"," +
        "    \"sizeClass\": \"LIGHT\", \"mass\": 900, \"maxDriveForce\": 6000, \"maxSteerAngle\": 40," +
        "    \"maxHP\": 200, \"armorValue\": 5, \"baySlots\": 1," +
        "    \"weapon\": { \"damage\": 25, \"fireRate\": 5, \"range\": 100, \"hitscan\": true," +
        "      \"damageType\": \"BALLISTIC\", \"firingMode\": \"AUTO\", \"magSize\": 80, \"reloadTime\": 2.0 } }\n" +
        "] }";

    @Test
    void loadsDefinitionsById() {
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        VehicleDefinition def = reg.get("rover_light");
        assertNotNull(def);
        assertEquals("Light Rover", def.displayName);
        assertEquals(900f, def.mass);
        assertEquals(1, def.baySlots);
        assertNotNull(def.weapon);
        assertEquals(DamageType.BALLISTIC, def.weapon.damageType);
        assertEquals(80, def.weapon.magSize);
    }

    @Test
    void unknownIdReturnsNull() {
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        assertNull(reg.get("nope"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.data.VehicleRegistryTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Create the registry**

`VehicleRegistry.java`:
```java
package com.galacticodyssey.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.data.VehicleDefinition.VehicleWeaponStats;

import java.util.HashMap;
import java.util.Map;

/** Loads and holds all {@link VehicleDefinition}s, looked up by id. */
public class VehicleRegistry {
    private final Map<String, VehicleDefinition> definitions = new HashMap<>();

    /** Loads from a classpath/internal file (used at game bootstrap). */
    public void load(String path) {
        loadFromJson(Gdx.files.internal(path).readString());
    }

    /** Parses definitions from a raw JSON string (unit-test friendly). */
    public void loadFromJson(String json) {
        JsonValue root = new JsonReader().parse(json);
        JsonValue arr = root.get("vehicles");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            VehicleDefinition def = new VehicleDefinition();
            def.id = e.getString("id");
            def.displayName = e.getString("displayName", def.id);
            def.modelPath = e.getString("modelPath", null);
            def.sizeClass = e.getString("sizeClass", "LIGHT");
            def.mass = e.getFloat("mass", def.mass);
            def.wheelbase = e.getFloat("wheelbase", def.wheelbase);
            def.trackWidth = e.getFloat("trackWidth", def.trackWidth);
            def.groundClearance = e.getFloat("groundClearance", def.groundClearance);
            def.maxDriveForce = e.getFloat("maxDriveForce", def.maxDriveForce);
            def.maxSteerAngle = e.getFloat("maxSteerAngle", def.maxSteerAngle);
            def.anchorBreakForce = e.getFloat("anchorBreakForce", def.anchorBreakForce);
            def.dynamicLift = e.getFloat("dynamicLift", def.dynamicLift);
            def.maxHP = e.getFloat("maxHP", def.maxHP);
            def.armorValue = e.getFloat("armorValue", def.armorValue);
            def.baySlots = e.getInt("baySlots", def.baySlots);

            JsonValue w = e.get("weapon");
            if (w != null) {
                VehicleWeaponStats ws = new VehicleWeaponStats();
                ws.damage = w.getFloat("damage", ws.damage);
                ws.fireRate = w.getFloat("fireRate", ws.fireRate);
                ws.range = w.getFloat("range", ws.range);
                ws.hitscan = w.getBoolean("hitscan", ws.hitscan);
                ws.projectileSpeed = w.getFloat("projectileSpeed", ws.projectileSpeed);
                ws.damageType = DamageType.valueOf(w.getString("damageType", ws.damageType.name()));
                ws.firingMode = FiringMode.valueOf(w.getString("firingMode", ws.firingMode.name()));
                ws.magSize = w.getInt("magSize", ws.magSize);
                ws.reloadTime = w.getFloat("reloadTime", ws.reloadTime);
                ws.spread = w.getFloat("spread", ws.spread);
                def.weapon = ws;
            }
            definitions.put(def.id, def);
        }
    }

    public VehicleDefinition get(String id) { return definitions.get(id); }

    public java.util.Collection<VehicleDefinition> all() { return definitions.values(); }
}
```

- [ ] **Step 4: Create the starter data file**

`core/src/main/resources/data/vehicles/vehicles.json`:
```json
{
  "vehicles": [
    {
      "id": "rover_light",
      "displayName": "Light Rover",
      "modelPath": "models/box.g3db",
      "sizeClass": "LIGHT",
      "mass": 900, "wheelbase": 2.6, "trackWidth": 1.8, "groundClearance": 0.45,
      "maxDriveForce": 6000, "maxSteerAngle": 42,
      "maxHP": 180, "armorValue": 4, "baySlots": 1,
      "weapon": { "damage": 18, "fireRate": 6, "range": 90, "hitscan": true,
        "damageType": "BALLISTIC", "firingMode": "AUTO", "magSize": 100, "reloadTime": 2.0, "spread": 2.0 }
    },
    {
      "id": "scout_armed",
      "displayName": "Armed Scout",
      "modelPath": "models/box.g3db",
      "sizeClass": "LIGHT",
      "mass": 1400, "wheelbase": 3.0, "trackWidth": 2.0, "groundClearance": 0.5,
      "maxDriveForce": 9000, "maxSteerAngle": 35,
      "maxHP": 320, "armorValue": 12, "baySlots": 1,
      "weapon": { "damage": 40, "fireRate": 3, "range": 140, "hitscan": false, "projectileSpeed": 220,
        "damageType": "BALLISTIC", "firingMode": "SEMI", "magSize": 30, "reloadTime": 3.0, "spread": 1.0 }
    },
    {
      "id": "tank_medium",
      "displayName": "Medium Tank",
      "modelPath": "models/box.g3db",
      "sizeClass": "MEDIUM",
      "mass": 8000, "wheelbase": 4.5, "trackWidth": 3.2, "groundClearance": 0.6,
      "maxDriveForce": 30000, "maxSteerAngle": 22,
      "maxHP": 900, "armorValue": 40, "baySlots": 2,
      "weapon": { "damage": 120, "fireRate": 0.8, "range": 200, "hitscan": false, "projectileSpeed": 300,
        "damageType": "EXPLOSIVE", "firingMode": "SEMI", "magSize": 12, "reloadTime": 4.0, "spread": 0.4 }
    }
  ]
}
```

(Note: `models/box.g3db` is a placeholder using whatever primitive box model the project already ships; the render hook in Task 15 falls back to a generated box if the model is missing.)

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.data.VehicleRegistryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/VehicleRegistry.java core/src/main/resources/data/vehicles/vehicles.json core/src/test/java/com/galacticodyssey/data/VehicleRegistryTest.java
git commit -m "feat(vehicle): add VehicleRegistry JSON loader and starter vehicle data"
```

---

### Task 5: `VehicleBayComponent` (storage on ships)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/components/VehicleBayComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/components/VehicleBayComponentTest.java`

(Snapshot persistence is wired in Task 17; for now this is a plain `Component`.)

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleBayComponentTest {
    @Test
    void tracksStoredVehiclesAndUsedSlots() {
        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 3;
        bay.storedVehicleIds.add("rover_light");
        bay.storedVehicleIds.add("tank_medium");
        assertEquals(2, bay.storedVehicleIds.size());
        assertTrue(bay.triggerRadius > 0f);
        assertNotNull(bay.localRampSpawnPosition);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.components.VehicleBayComponentTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Create the component**

```java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

/** A ship's vehicle bay: stored vehicle definition ids plus deploy/retrieve geometry. */
public class VehicleBayComponent implements Component {
    public int capacity = 2;
    public final List<String> storedVehicleIds = new ArrayList<>();
    /** Ship-local point beside the ramp where deployed vehicles spawn. */
    public final Vector3 localRampSpawnPosition = new Vector3(0f, 0f, 6f);
    /** Proximity to the ramp within which a driven vehicle can be retrieved. */
    public float triggerRadius = 6f;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.components.VehicleBayComponentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/components/VehicleBayComponent.java core/src/test/java/com/galacticodyssey/ship/components/VehicleBayComponentTest.java
git commit -m "feat(ship): add VehicleBayComponent for vehicle storage"
```

---

## Phase 2 — Entity assembly (headless + Bullet)

### Task 6: `VehicleFactory`

Builds a combat-ready vehicle entity. Takes the engine, the exterior `btDiscreteDynamicsWorld`, a `VehicleDefinition`, and a world spawn position. Adds the entity to the engine and the body to the world. No GL — the model is represented only by `VehicleRenderComponent.modelPath`.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/VehicleFactoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleFactoryTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private btDiscreteDynamicsWorld newWorld() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        return new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
    }

    private VehicleDefinition def() {
        VehicleDefinition d = new VehicleDefinition();
        d.id = "rover_light";
        d.mass = 900f; d.maxHP = 200f; d.armorValue = 8f;
        d.modelPath = "models/box.g3db";
        d.weapon = new VehicleDefinition.VehicleWeaponStats();
        d.weapon.damage = 25f; d.weapon.magSize = 50; d.weapon.range = 100f;
        return d;
    }

    @Test
    void buildsEntityWithAllComponents() {
        Engine engine = new Engine();
        btDiscreteDynamicsWorld world = newWorld();
        VehicleFactory factory = new VehicleFactory();

        Entity v = factory.create(engine, world, def(), new Vector3(10, 0, 5));

        assertNotNull(v.getComponent(VehicleTagComponent.class));
        assertEquals("rover_light", v.getComponent(VehicleTagComponent.class).definitionId);
        assertNotNull(v.getComponent(TransformComponent.class));
        assertNotNull(v.getComponent(GroundVehicleComponent.class));
        assertNotNull(v.getComponent(PhysicsBodyComponent.class));
        assertNotNull(v.getComponent(PhysicsBodyComponent.class).body);
        assertNotNull(v.getComponent(HealthComponent.class));
        assertEquals(200f, v.getComponent(HealthComponent.class).maxHP);
        assertNotNull(v.getComponent(HitboxComponent.class));
        assertNotNull(v.getComponent(RangedWeaponComponent.class));
        assertEquals(50, v.getComponent(RangedWeaponComponent.class).currentAmmo);
        assertNotNull(v.getComponent(CombatInputComponent.class));
        assertNotNull(v.getComponent(VehicleEntryPointComponent.class));
        assertEquals("models/box.g3db", v.getComponent(VehicleRenderComponent.class).modelPath);
        // Entity registered with engine
        assertTrue(engine.getEntities().contains(v, true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleFactoryTest"`
Expected: FAIL — `VehicleFactory` not found.

- [ ] **Step 3: Create the factory**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleDefinition;
import com.galacticodyssey.data.VehicleDefinition.VehicleWeaponStats;

/** Builds a fully combat-ready ground-vehicle entity from a {@link VehicleDefinition}. */
public class VehicleFactory {

    /**
     * Creates the vehicle entity, registers it with {@code engine} and adds its rigid body to
     * {@code world}. The render layer attaches a model later via {@link VehicleRenderComponent}.
     */
    public Entity create(Engine engine, btDiscreteDynamicsWorld world,
                         VehicleDefinition def, Vector3 spawnPos) {
        Entity e = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnPos);
        e.add(transform);

        VehicleTagComponent tag = new VehicleTagComponent();
        tag.definitionId = def.id;
        e.add(tag);

        GroundVehicleComponent gv = new GroundVehicleComponent();
        gv.mass = def.mass;
        gv.wheelbase = def.wheelbase;
        gv.trackWidth = def.trackWidth;
        gv.groundClearance = def.groundClearance;
        gv.maxDriveForce = def.maxDriveForce;
        gv.maxSteerAngle = def.maxSteerAngle;
        gv.anchorBreakForce = def.anchorBreakForce;
        gv.dynamicLift = def.dynamicLift;
        e.add(gv);

        e.add(buildPhysics(def, spawnPos, world));

        HealthComponent health = new HealthComponent();
        health.maxHP = def.maxHP;
        health.currentHP = def.maxHP;
        health.alive = true;
        e.add(health);

        ArmorComponent armor = new ArmorComponent();
        for (HitRegion region : HitRegion.values()) {
            armor.armorRating.put(region, def.armorValue);
        }
        e.add(armor);

        HitboxComponent hitbox = new HitboxComponent();
        hitbox.bodyHeight = Math.max(1f, def.groundClearance + 1.5f);
        e.add(hitbox);

        if (def.weapon != null) {
            e.add(buildWeapon(def.weapon));
            e.add(new CombatInputComponent());
        }

        VehicleEntryPointComponent entry = new VehicleEntryPointComponent();
        entry.triggerRadius = Math.max(2.5f, def.trackWidth + 1.5f);
        e.add(entry);

        VehicleRenderComponent render = new VehicleRenderComponent();
        render.modelPath = def.modelPath;
        e.add(render);

        engine.addEntity(e);
        return e;
    }

    private PhysicsBodyComponent buildPhysics(VehicleDefinition def, Vector3 spawnPos,
                                              btDiscreteDynamicsWorld world) {
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(
            def.trackWidth * 0.5f,
            Math.max(0.5f, def.groundClearance + 0.5f) * 0.5f,
            def.wheelbase * 0.5f));
        physics.mass = def.mass;
        physics.friction = 0.8f;
        physics.restitution = 0f;

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(spawnPos));
        physics.body.setFriction(physics.friction);
        physics.body.setDamping(0.15f, 0.6f);
        info.dispose();

        world.addRigidBody(physics.body);
        return physics;
    }

    private RangedWeaponComponent buildWeapon(VehicleWeaponStats w) {
        RangedWeaponComponent ranged = new RangedWeaponComponent();
        ranged.damage = w.damage;
        ranged.fireRate = w.fireRate;
        ranged.range = w.range;
        ranged.hitscan = w.hitscan;
        ranged.projectileSpeed = w.projectileSpeed;
        ranged.damageType = w.damageType;
        ranged.firingMode = w.firingMode;
        ranged.magSize = w.magSize;
        ranged.currentAmmo = w.magSize;
        ranged.reloadTime = w.reloadTime;
        ranged.spread = w.spread;
        return ranged;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleFactoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/VehicleFactory.java core/src/test/java/com/galacticodyssey/planet/terrain/VehicleFactoryTest.java
git commit -m "feat(vehicle): add VehicleFactory assembling combat-ready vehicle entities"
```

---

## Phase 3 — Driving simulation (headless + Bullet)

### Task 7: Make `SurfaceVehicleSystem` consume driver throttle/steer

Replace the hardcoded `throttleInput = 1.0f` with `vehicle.throttleInput`, support reverse, suppress idle wheel-slip, and add steering torque from `vehicle.steerInput`.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/SurfaceVehicleSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/SurfaceVehicleDrivingTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceVehicleDrivingTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private btDiscreteDynamicsWorld world;

    private Entity makeVehicle(Engine engine, float mass) {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));

        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        e.add(t);
        GroundVehicleComponent gv = new GroundVehicleComponent();
        gv.mass = mass; gv.maxDriveForce = 8000f; gv.maxSteerAngle = 35f;
        gv.wheelbase = 3f; gv.trackWidth = 2f;
        e.add(gv);
        PhysicsBodyComponent p = new PhysicsBodyComponent();
        p.shape = new btBoxShape(new Vector3(1, 0.5f, 1.5f));
        p.mass = mass;
        Vector3 inertia = new Vector3();
        p.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, p.shape, inertia);
        p.body = new btRigidBody(info);
        p.body.setWorldTransform(new Matrix4().idt());
        info.dispose();
        e.add(p);
        engine.addEntity(e);
        world.addRigidBody(p.body);
        return e;
    }

    @Test
    void fullThrottleAccelerates() {
        Engine engine = new Engine();
        SurfaceVehicleSystem sys = new SurfaceVehicleSystem(null, new EventBus());
        engine.addSystem(sys);
        Entity v = makeVehicle(engine, 1000f);
        v.getComponent(GroundVehicleComponent.class).throttleInput = 1f;

        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) { sys.update(dt); world.stepSimulation(dt, 1, dt); }

        float speed = v.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertTrue(speed > 0.5f, "expected motion under throttle, speed=" + speed);
    }

    @Test
    void zeroThrottleStaysStill() {
        Engine engine = new Engine();
        SurfaceVehicleSystem sys = new SurfaceVehicleSystem(null, new EventBus());
        engine.addSystem(sys);
        Entity v = makeVehicle(engine, 1000f);
        v.getComponent(GroundVehicleComponent.class).throttleInput = 0f;

        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) { sys.update(dt); world.stepSimulation(dt, 1, dt); }

        float speed = v.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertTrue(speed < 0.01f, "expected no motion at zero throttle, speed=" + speed);
    }

    @Test
    void steerInputProducesYaw() {
        Engine engine = new Engine();
        SurfaceVehicleSystem sys = new SurfaceVehicleSystem(null, new EventBus());
        engine.addSystem(sys);
        Entity v = makeVehicle(engine, 1000f);
        v.getComponent(GroundVehicleComponent.class).steerInput = 1f;

        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) { sys.update(dt); world.stepSimulation(dt, 1, dt); }

        float yawRate = Math.abs(v.getComponent(PhysicsBodyComponent.class).body.getAngularVelocity().y);
        assertTrue(yawRate > 0.001f, "expected yaw from steering, yawRate=" + yawRate);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.SurfaceVehicleDrivingTest"`
Expected: FAIL — `zeroThrottleStaysStill` fails (hardcoded throttle drives it) and `steerInputProducesYaw` fails (no steering yet).

- [ ] **Step 3: Modify `processTraction` and add steering**

In `SurfaceVehicleSystem.java`, add a steer-torque constant near the top of the class:
```java
    private static final float MAX_SINKAGE = 0.5f;
    private static final float SINKAGE_ROLLING_RESISTANCE_COEFF = 0.05f;
    private static final float STEER_TORQUE_SCALE = 0.04f;
```

Add a reusable up-vector temp alongside the other temps:
```java
    private final Vector3 tmpUp = new Vector3();
```

Replace the body of `processTraction(...)` with:
```java
    private void processTraction(Entity entity, GroundVehicleComponent vehicle,
                                  PhysicsBodyComponent physics, TransformComponent transform,
                                  SurfaceProperties surface, float gravMag, float deltaTime) {
        final float normalForce = vehicle.mass * gravMag;
        final float maxTraction = normalForce * surface.kineticFriction;

        final float requestedForce = vehicle.maxDriveForce * vehicle.throttleInput;
        final float requestedMag = Math.abs(requestedForce);

        if (requestedMag <= 0.001f) {
            vehicle.slipFraction = 0f;
        } else {
            final float clampedMag = Math.min(requestedMag, maxTraction);
            vehicle.slipFraction = MathUtils.clamp(1f - clampedMag / requestedMag, 0f, 1f);
            if (vehicle.slipFraction > 0.1f) {
                eventBus.publish(new WheelSlipEvent(entity, vehicle.slipFraction));
            }
            final float actualForce = Math.signum(requestedForce) * clampedMag;
            physics.body.getWorldTransform(tmpMat);
            tmpMat.getRotation(tmpQuat);
            tmpForward.set(0f, 0f, -1f).mul(tmpQuat).nor();
            physics.body.applyCentralForce(tmpForward.scl(actualForce));
            physics.body.activate();
        }

        applySteering(vehicle, physics);
    }

    private void applySteering(GroundVehicleComponent vehicle, PhysicsBodyComponent physics) {
        if (Math.abs(vehicle.steerInput) <= 0.001f) return;
        physics.body.getWorldTransform(tmpMat);
        tmpMat.getRotation(tmpQuat);
        tmpUp.set(0f, 1f, 0f).mul(tmpQuat).nor();
        final float torque = vehicle.steerInput * vehicle.maxSteerAngle * vehicle.mass * STEER_TORQUE_SCALE;
        physics.body.applyTorque(tmpUp.scl(torque));
        physics.body.activate();
    }
```

(The `processRegolithSinkage` drag term already keys off forward speed, so at zero throttle and zero velocity it contributes no force — no change needed there.)

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.SurfaceVehicleDrivingTest"`
Expected: PASS (all three).

- [ ] **Step 5: Run the existing surface-vehicle tests to check for regressions**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.*"`
Expected: PASS. (If a prior test relied on the old hardcoded throttle, set `throttleInput = 1f` in that test's setup.)

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/SurfaceVehicleSystem.java core/src/test/java/com/galacticodyssey/planet/terrain/SurfaceVehicleDrivingTest.java
git commit -m "feat(vehicle): drive SurfaceVehicleSystem from throttle/steer input"
```

---

### Task 8: Add `DRIVING` mode + `currentVehicle`; guard `PlayerMovementSystem`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/components/PlayerStateComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementDrivingGuardTest.java`

- [ ] **Step 1: Write the failing test**

This test asserts the enum value exists and that `PlayerMovementSystem` skips movement while `DRIVING`. Inspect `PlayerMovementSystem` first to see how it early-returns for `PILOTING` and mirror that exact guard; the test below checks the observable contract (position unchanged while DRIVING with forward input).

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerMovementDrivingGuardTest {
    @Test
    void drivingModeExists() {
        assertNotNull(PlayerMode.valueOf("DRIVING"));
    }

    @Test
    void stateHoldsCurrentVehicle() {
        PlayerStateComponent s = new PlayerStateComponent();
        Entity v = new Entity();
        s.currentVehicle = v;
        assertSame(v, s.currentVehicle);
    }
}
```

(A full physics-driven "position unchanged" assertion requires the player capsule harness; the enum + guard wiring is verified here and in the integration run in Task 16. The guard itself is a one-line early-return mirroring the existing `PILOTING` guard.)

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.player.systems.PlayerMovementDrivingGuardTest"`
Expected: FAIL — `No enum constant ... DRIVING`.

- [ ] **Step 3: Add the enum value and field**

In `PlayerStateComponent.java`:
```java
    public enum PlayerMode {
        ON_FOOT_EXTERIOR,
        ON_FOOT_INTERIOR,
        PILOTING,
        DRIVING
    }
```
Add the field after `currentShip`:
```java
    public Entity currentShip;
    public Entity currentVehicle;
```
Also add a persisted id alongside the others (used in Task 17 wiring; harmless now):
```java
    public UUID currentShipId;
    public UUID currentVehicleId;
    public UUID interactionTargetId;
```

- [ ] **Step 4: Guard `PlayerMovementSystem`**

Open `PlayerMovementSystem.java`, find the existing early return:
```java
        if (playerState.currentMode == PlayerMode.PILOTING) return;
```
Change it to also cover DRIVING:
```java
        if (playerState.currentMode == PlayerMode.PILOTING
                || playerState.currentMode == PlayerMode.DRIVING) return;
```
(If the variable is named differently, match the surrounding code. If the guard reads `!= ON_FOOT_*`, adapt equivalently so DRIVING is excluded from on-foot movement.)

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.player.systems.PlayerMovementDrivingGuardTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/PlayerStateComponent.java core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementDrivingGuardTest.java
git commit -m "feat(player): add DRIVING mode + currentVehicle, guard on-foot movement"
```

---

### Task 9: `VehicleControlSystem` (driver input → vehicle) + suppress player FPS weapon while mounted

Runs only when the player is `DRIVING`. Maps `PlayerInputComponent.moveForward` → `throttleInput`, `moveStrafe` → `steerInput`, mirrors the player's `CombatInputComponent` fire flags onto the vehicle's, and sets the vehicle's `aimDirection` to the vehicle's forward vector.

**Problem this task also fixes:** the player entity keeps its FPS `RangedWeaponComponent` + `WeaponInventoryComponent` + `CombatInputComponent` while driving, and the shared `WeaponSystem` (priority 4) is not gated by `PlayerMode` — so pressing fire would shoot the FPS weapon *and* the vehicle weapon. We add a **per-entity** guard to `WeaponSystem` that skips firing only for an entity whose `PlayerStateComponent` is `PILOTING` or `DRIVING`. This is targeted (NPCs have no `PlayerStateComponent`, so their firing is unaffected) and also fixes the latent same-bug for `PILOTING`.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleControlSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/systems/WeaponSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/VehicleControlSystemTest.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/WeaponSystemDrivingGuardTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleControlSystemTest {

    private Entity vehicle() {
        Entity v = new Entity();
        v.add(new TransformComponent());
        v.add(new GroundVehicleComponent());
        v.add(new CombatInputComponent());
        v.add(new VehicleTagComponent());
        return v;
    }

    private Entity player(PlayerMode mode, Entity vehicle, float fwd, float strafe, boolean fire) {
        Entity p = new Entity();
        p.add(new PlayerTagComponent());
        PlayerInputComponent in = new PlayerInputComponent();
        in.moveForward = fwd; in.moveStrafe = strafe;
        p.add(in);
        CombatInputComponent ci = new CombatInputComponent();
        ci.fireHeld = fire;
        p.add(ci);
        PlayerStateComponent st = new PlayerStateComponent();
        st.currentMode = mode; st.currentVehicle = vehicle;
        p.add(st);
        return p;
    }

    @Test
    void drivingRoutesInputToVehicle() {
        Engine engine = new Engine();
        VehicleControlSystem sys = new VehicleControlSystem();
        engine.addSystem(sys);
        Entity v = vehicle();
        engine.addEntity(v);
        engine.addEntity(player(PlayerMode.DRIVING, v, 1f, -1f, true));

        sys.update(1f / 60f);

        GroundVehicleComponent gv = v.getComponent(GroundVehicleComponent.class);
        assertEquals(1f, gv.throttleInput, 1e-4);
        assertEquals(-1f, gv.steerInput, 1e-4);
        assertTrue(v.getComponent(CombatInputComponent.class).fireHeld);
        assertTrue(v.getComponent(CombatInputComponent.class).aimDirection.len() > 0.5f);
    }

    @Test
    void notDrivingLeavesVehicleInputZero() {
        Engine engine = new Engine();
        VehicleControlSystem sys = new VehicleControlSystem();
        engine.addSystem(sys);
        Entity v = vehicle();
        engine.addEntity(v);
        engine.addEntity(player(PlayerMode.ON_FOOT_EXTERIOR, v, 1f, 1f, true));

        sys.update(1f / 60f);

        assertEquals(0f, v.getComponent(GroundVehicleComponent.class).throttleInput, 1e-4);
        assertFalse(v.getComponent(CombatInputComponent.class).fireHeld);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleControlSystemTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Create the system**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;

/** Routes driver input to the controlled vehicle while the player is in DRIVING mode. */
public class VehicleControlSystem extends EntitySystem {

    public static final int PRIORITY = 0;

    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<PlayerInputComponent> INPUT_M =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private static final ComponentMapper<CombatInputComponent> COMBAT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<GroundVehicleComponent> VEHICLE_M =
        ComponentMapper.getFor(GroundVehicleComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> players;
    private final Matrix4 tmpMat = new Matrix4();
    private final Quaternion tmpQuat = new Quaternion();
    private final Vector3 tmpForward = new Vector3();

    public VehicleControlSystem() { super(PRIORITY); }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class,
            PlayerInputComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < players.size(); i++) {
            Entity player = players.get(i);
            PlayerStateComponent state = STATE_M.get(player);
            if (state.currentMode != PlayerMode.DRIVING || state.currentVehicle == null) continue;

            Entity vehicle = state.currentVehicle;
            GroundVehicleComponent gv = VEHICLE_M.get(vehicle);
            if (gv == null) continue;

            PlayerInputComponent in = INPUT_M.get(player);
            gv.throttleInput = MathUtils.clamp(in.moveForward, -1f, 1f);
            gv.steerInput = MathUtils.clamp(in.moveStrafe, -1f, 1f);

            CombatInputComponent vehicleCombat = COMBAT_M.get(vehicle);
            CombatInputComponent playerCombat = COMBAT_M.get(player);
            if (vehicleCombat != null) {
                if (playerCombat != null) {
                    vehicleCombat.fireRequested = playerCombat.fireRequested;
                    vehicleCombat.fireHeld = playerCombat.fireHeld;
                }
                TransformComponent t = TRANSFORM_M.get(vehicle);
                if (t != null) {
                    tmpMat.set(t.position, t.rotation);
                    tmpMat.getRotation(tmpQuat);
                    tmpForward.set(0f, 0f, -1f).mul(tmpQuat).nor();
                    vehicleCombat.aimDirection.set(tmpForward);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleControlSystemTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing WeaponSystem-guard test**

```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WeaponSystemDrivingGuardTest {
    private Entity playerWithWeapon(PlayerMode mode) {
        Entity p = new Entity();
        p.add(new TransformComponent());
        RangedWeaponComponent w = new RangedWeaponComponent();
        w.firingMode = FiringMode.AUTO; w.fireRate = 5f; w.magSize = 30; w.currentAmmo = 30; w.hitscan = true;
        p.add(w);
        p.add(new WeaponInventoryComponent());
        CombatInputComponent ci = new CombatInputComponent();
        ci.fireHeld = true;
        p.add(ci);
        PlayerStateComponent st = new PlayerStateComponent();
        st.currentMode = mode;
        p.add(st);
        return p;
    }

    @Test
    void doesNotFirePlayerWeaponWhileDriving() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        WeaponSystem sys = new WeaponSystem(bus);
        engine.addSystem(sys);
        engine.addEntity(playerWithWeapon(PlayerMode.DRIVING));

        sys.update(1f / 60f);
        assertEquals(0, shots.get());
    }

    @Test
    void firesPlayerWeaponOnFoot() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        WeaponSystem sys = new WeaponSystem(bus);
        engine.addSystem(sys);
        engine.addEntity(playerWithWeapon(PlayerMode.ON_FOOT_EXTERIOR));

        sys.update(1f / 60f);
        assertEquals(1, shots.get());
    }
}
```

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.combat.systems.WeaponSystemDrivingGuardTest"`
Expected: FAIL — `doesNotFirePlayerWeaponWhileDriving` fires (no guard yet).

- [ ] **Step 6: Add the per-entity guard to `WeaponSystem`**

In `WeaponSystem.java`, add a mapper field alongside the others:
```java
    private static final ComponentMapper<com.galacticodyssey.player.components.PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(com.galacticodyssey.player.components.PlayerStateComponent.class);
```
At the very top of `processEntity(...)`, before reading the other components, add:
```java
        com.galacticodyssey.player.components.PlayerStateComponent state = STATE_M.get(entity);
        if (state != null
                && (state.currentMode == com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode.PILOTING
                 || state.currentMode == com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode.DRIVING)) {
            CombatInputComponent input = INPUT_M.get(entity);
            input.fireRequested = false;
            return;
        }
```

- [ ] **Step 7: Run both tests to verify they pass**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.combat.systems.WeaponSystemDrivingGuardTest" --tests "com.galacticodyssey.combat.systems.WeaponSystemTest"`
Expected: PASS (new guard works; existing WeaponSystem behavior unaffected for on-foot/NPC entities).

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/VehicleControlSystem.java core/src/main/java/com/galacticodyssey/combat/systems/WeaponSystem.java core/src/test/java/com/galacticodyssey/planet/terrain/VehicleControlSystemTest.java core/src/test/java/com/galacticodyssey/combat/systems/WeaponSystemDrivingGuardTest.java
git commit -m "feat(vehicle): add VehicleControlSystem + suppress player FPS weapon while mounted"
```

---

### Task 10: `VehicleWeaponSystem` (thin fire trigger)

Reads the vehicle's `CombatInputComponent` + `RangedWeaponComponent`, manages the fire-rate timer / ammo / simple reload, and publishes `WeaponFiredEvent` — the exact event the player's `WeaponSystem` publishes, so `HitscanSystem`/`ProjectileSystem` handle it downstream. SEMI + AUTO only.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleWeaponSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/VehicleWeaponSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VehicleWeaponSystemTest {

    private Entity vehicle(FiringMode mode, int ammo) {
        Entity v = new Entity();
        v.add(new TransformComponent());
        v.add(new VehicleTagComponent());
        RangedWeaponComponent w = new RangedWeaponComponent();
        w.firingMode = mode; w.fireRate = 5f; w.magSize = 30; w.currentAmmo = ammo;
        w.hitscan = true; w.damage = 10f; w.range = 100f;
        v.add(w);
        v.add(new CombatInputComponent());
        return v;
    }

    @Test
    void firesAndConsumesAmmoOnInput() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        VehicleWeaponSystem sys = new VehicleWeaponSystem(bus);
        engine.addSystem(sys);
        Entity v = vehicle(FiringMode.AUTO, 30);
        v.getComponent(CombatInputComponent.class).fireHeld = true;
        engine.addEntity(v);

        sys.update(1f / 60f);

        assertEquals(1, shots.get());
        assertEquals(29, v.getComponent(RangedWeaponComponent.class).currentAmmo);
    }

    @Test
    void doesNotFireWhenMagEmpty() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        VehicleWeaponSystem sys = new VehicleWeaponSystem(bus);
        engine.addSystem(sys);
        Entity v = vehicle(FiringMode.AUTO, 0);
        v.getComponent(CombatInputComponent.class).fireHeld = true;
        engine.addEntity(v);

        sys.update(1f / 60f);

        assertEquals(0, shots.get());
    }

    @Test
    void respectsFireRateCooldown() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        VehicleWeaponSystem sys = new VehicleWeaponSystem(bus);
        engine.addSystem(sys);
        Entity v = vehicle(FiringMode.AUTO, 30); // fireRate 5 => 0.2s between shots
        v.getComponent(CombatInputComponent.class).fireHeld = true;
        engine.addEntity(v);

        sys.update(1f / 60f); // fires
        sys.update(1f / 60f); // still on cooldown
        assertEquals(1, shots.get());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleWeaponSystemTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Create the system**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Thin firing trigger for vehicle weapons. Advances the fire-rate timer, decrements ammo,
 * handles a simple reload, and publishes {@link WeaponFiredEvent} so the existing
 * HitscanSystem/ProjectileSystem/DamageSystem pipeline handles the shot. SEMI + AUTO only.
 */
public class VehicleWeaponSystem extends IteratingSystem {

    public static final int PRIORITY = 4;

    private final EventBus eventBus;

    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<RangedWeaponComponent> WEAPON_M =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    public VehicleWeaponSystem(EventBus eventBus) {
        super(Family.all(VehicleTagComponent.class, CombatInputComponent.class,
                         RangedWeaponComponent.class, TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = INPUT_M.get(entity);
        RangedWeaponComponent w = WEAPON_M.get(entity);

        if (w.fireTimer > 0f) w.fireTimer -= deltaTime;

        if (w.reloading) {
            w.reloadTimer -= deltaTime;
            if (w.reloadTimer <= 0f) {
                w.currentAmmo = w.magSize;
                w.reloading = false;
                w.reloadTimer = 0f;
            }
            input.fireRequested = false;
            return;
        }

        boolean wantsFire = input.fireRequested || input.fireHeld;
        if (!wantsFire) { input.fireRequested = false; return; }

        if (w.currentAmmo <= 0) {
            if (w.reloadTime > 0f) {
                w.reloading = true;
                w.reloadTimer = w.reloadTime;
            }
            input.fireRequested = false;
            return;
        }

        FiringMode mode = w.firingMode != null ? w.firingMode : FiringMode.AUTO;
        boolean trigger = (mode == FiringMode.SEMI) ? input.fireRequested : wantsFire;
        if (trigger && w.fireTimer <= 0f) {
            fire(entity, w, input);
        }
        input.fireRequested = false;
    }

    private void fire(Entity entity, RangedWeaponComponent w, CombatInputComponent input) {
        w.currentAmmo--;
        w.fireTimer = w.fireRate > 0f ? 1f / w.fireRate : 0f;
        Vector3 muzzle = new Vector3(TRANSFORM_M.get(entity).position);
        eventBus.publish(new WeaponFiredEvent(entity, input.aimDirection, w.hitscan, muzzle));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleWeaponSystemTest"`
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/VehicleWeaponSystem.java core/src/test/java/com/galacticodyssey/planet/terrain/VehicleWeaponSystemTest.java
git commit -m "feat(vehicle): add VehicleWeaponSystem publishing WeaponFiredEvent"
```

---

## Phase 4 — Bay deploy/retrieve + interaction (headless)

### Task 11: Vehicle/bay events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/events/VehicleDeployedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/events/VehicleRetrievedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PlayerEnterVehicleEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PlayerExitVehicleEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/events/VehicleEventsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.events.PlayerEnterVehicleEvent;
import com.galacticodyssey.core.events.PlayerExitVehicleEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEventsTest {
    @Test
    void eventsCarryReferences() {
        Entity ship = new Entity();
        Entity vehicle = new Entity();
        Entity player = new Entity();

        VehicleDeployedEvent dep = new VehicleDeployedEvent(vehicle, ship);
        assertSame(vehicle, dep.vehicle);
        assertSame(ship, dep.ship);

        VehicleRetrievedEvent ret = new VehicleRetrievedEvent("rover_light", ship);
        assertEquals("rover_light", ret.vehicleDefinitionId);
        assertSame(ship, ret.ship);

        PlayerEnterVehicleEvent enter = new PlayerEnterVehicleEvent(player, vehicle);
        assertSame(player, enter.player);
        assertSame(vehicle, enter.vehicle);

        PlayerExitVehicleEvent exit = new PlayerExitVehicleEvent(player, vehicle);
        assertSame(player, exit.player);
        assertSame(vehicle, exit.vehicle);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.events.VehicleEventsTest"`
Expected: FAIL — classes not found.

- [ ] **Step 3: Create the four event classes**

`VehicleDeployedEvent.java`:
```java
package com.galacticodyssey.planet.terrain.events;

import com.badlogic.ashley.core.Entity;

public final class VehicleDeployedEvent {
    public final Entity vehicle;
    public final Entity ship;
    public VehicleDeployedEvent(Entity vehicle, Entity ship) {
        this.vehicle = vehicle; this.ship = ship;
    }
}
```

`VehicleRetrievedEvent.java`:
```java
package com.galacticodyssey.planet.terrain.events;

import com.badlogic.ashley.core.Entity;

public final class VehicleRetrievedEvent {
    public final String vehicleDefinitionId;
    public final Entity ship;
    public VehicleRetrievedEvent(String vehicleDefinitionId, Entity ship) {
        this.vehicleDefinitionId = vehicleDefinitionId; this.ship = ship;
    }
}
```

`PlayerEnterVehicleEvent.java`:
```java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerEnterVehicleEvent {
    public final Entity player;
    public final Entity vehicle;
    public PlayerEnterVehicleEvent(Entity player, Entity vehicle) {
        this.player = player; this.vehicle = vehicle;
    }
}
```

`PlayerExitVehicleEvent.java`:
```java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerExitVehicleEvent {
    public final Entity player;
    public final Entity vehicle;
    public PlayerExitVehicleEvent(Entity player, Entity vehicle) {
        this.player = player; this.vehicle = vehicle;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.events.VehicleEventsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/events/ core/src/main/java/com/galacticodyssey/core/events/PlayerEnterVehicleEvent.java core/src/main/java/com/galacticodyssey/core/events/PlayerExitVehicleEvent.java core/src/test/java/com/galacticodyssey/planet/terrain/events/VehicleEventsTest.java
git commit -m "feat(vehicle): add deploy/retrieve and enter/exit-vehicle events"
```

---

### Task 12: `VehicleBayService` (deploy / retrieve / capacity)

Coordinates bay state changes. Holds references to the engine, exterior dynamics world, `VehicleRegistry`, `VehicleFactory`, and `EventBus`. Computes the world spawn position from the ship transform + `localRampSpawnPosition`.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleBayService.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/VehicleBayServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleDefinition;
import com.galacticodyssey.data.VehicleRegistry;
import com.galacticodyssey.planet.terrain.events.VehicleDeployedEvent;
import com.galacticodyssey.planet.terrain.events.VehicleRetrievedEvent;
import com.galacticodyssey.ship.components.VehicleBayComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class VehicleBayServiceTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private static final String JSON = "{ \"vehicles\": [ { \"id\": \"rover_light\"," +
        " \"mass\": 900, \"maxHP\": 200, \"baySlots\": 1," +
        " \"weapon\": { \"damage\": 10, \"magSize\": 20 } } ] }";

    private btDiscreteDynamicsWorld newWorld() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        return new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
    }

    private Entity ship(VehicleBayComponent bay) {
        Entity s = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(100, 0, 100);
        s.add(t);
        s.add(bay);
        return s;
    }

    @Test
    void deploySpawnsVehicleAndRemovesFromBay() {
        Engine engine = new Engine();
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        EventBus bus = new EventBus();
        AtomicReference<VehicleDeployedEvent> evt = new AtomicReference<>();
        bus.subscribe(VehicleDeployedEvent.class, evt::set);

        VehicleBayService service =
            new VehicleBayService(engine, newWorld(), reg, new VehicleFactory(), bus);

        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 2;
        bay.storedVehicleIds.add("rover_light");
        bay.localRampSpawnPosition.set(0, 0, 6);
        Entity shipEntity = ship(bay);

        Entity vehicle = service.deploy(shipEntity, "rover_light");

        assertNotNull(vehicle);
        assertEquals(0, bay.storedVehicleIds.size());
        assertNotNull(evt.get());
        assertSame(vehicle, evt.get().vehicle);
        // spawned at ship pos + local offset
        Vector3 pos = vehicle.getComponent(TransformComponent.class).position;
        assertEquals(106f, pos.z, 0.001f);
    }

    @Test
    void deployUnknownOrAbsentReturnsNull() {
        Engine engine = new Engine();
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        VehicleBayService service =
            new VehicleBayService(engine, newWorld(), reg, new VehicleFactory(), new EventBus());
        VehicleBayComponent bay = new VehicleBayComponent();
        Entity shipEntity = ship(bay);

        assertNull(service.deploy(shipEntity, "rover_light")); // not in bay
    }

    @Test
    void retrieveReturnsToBayWhenCapacityAllows() {
        Engine engine = new Engine();
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        EventBus bus = new EventBus();
        AtomicReference<VehicleRetrievedEvent> evt = new AtomicReference<>();
        bus.subscribe(VehicleRetrievedEvent.class, evt::set);
        VehicleBayService service =
            new VehicleBayService(engine, newWorld(), reg, new VehicleFactory(), bus);

        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 2;
        bay.storedVehicleIds.add("rover_light");
        Entity shipEntity = ship(bay);
        Entity vehicle = service.deploy(shipEntity, "rover_light");
        assertNotNull(vehicle);

        boolean ok = service.retrieve(shipEntity, vehicle);

        assertTrue(ok);
        assertEquals(1, bay.storedVehicleIds.size());
        assertEquals("rover_light", bay.storedVehicleIds.get(0));
        assertFalse(engine.getEntities().contains(vehicle, true));
        assertNotNull(evt.get());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleBayServiceTest"`
Expected: FAIL — `VehicleBayService` not found.

- [ ] **Step 3: Create the service**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleDefinition;
import com.galacticodyssey.data.VehicleRegistry;
import com.galacticodyssey.planet.terrain.events.VehicleDeployedEvent;
import com.galacticodyssey.planet.terrain.events.VehicleRetrievedEvent;
import com.galacticodyssey.ship.components.VehicleBayComponent;

/** Deploy/retrieve coordinator for a ship's vehicle bay. */
public class VehicleBayService {

    private final Engine engine;
    private final btDiscreteDynamicsWorld world;
    private final VehicleRegistry registry;
    private final VehicleFactory factory;
    private final EventBus eventBus;

    private final Matrix4 tmpMat = new Matrix4();
    private final Vector3 tmpSpawn = new Vector3();

    public VehicleBayService(Engine engine, btDiscreteDynamicsWorld world,
                             VehicleRegistry registry, VehicleFactory factory, EventBus eventBus) {
        this.engine = engine;
        this.world = world;
        this.registry = registry;
        this.factory = factory;
        this.eventBus = eventBus;
    }

    /** Spawns a stored vehicle beside the ship ramp. Returns null if it isn't stored/known. */
    public Entity deploy(Entity ship, String vehicleDefinitionId) {
        VehicleBayComponent bay = ship.getComponent(VehicleBayComponent.class);
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        if (bay == null || shipTransform == null) return null;
        if (!bay.storedVehicleIds.contains(vehicleDefinitionId)) return null;
        VehicleDefinition def = registry.get(vehicleDefinitionId);
        if (def == null) return null;

        tmpMat.set(shipTransform.position, shipTransform.rotation);
        tmpSpawn.set(bay.localRampSpawnPosition).mul(tmpMat);

        Entity vehicle = factory.create(engine, world, def, tmpSpawn);
        bay.storedVehicleIds.remove(vehicleDefinitionId);
        eventBus.publish(new VehicleDeployedEvent(vehicle, ship));
        return vehicle;
    }

    /** Returns a deployed vehicle to the bay (if capacity allows). Returns false otherwise. */
    public boolean retrieve(Entity ship, Entity vehicle) {
        VehicleBayComponent bay = ship.getComponent(VehicleBayComponent.class);
        VehicleTagComponent tag = vehicle.getComponent(VehicleTagComponent.class);
        if (bay == null || tag == null) return false;
        if (usedSlots(bay) >= bay.capacity) return false;

        PhysicsBodyComponent physics = vehicle.getComponent(PhysicsBodyComponent.class);
        if (physics != null && physics.body != null) {
            world.removeRigidBody(physics.body);
        }
        engine.removeEntity(vehicle);
        bay.storedVehicleIds.add(tag.definitionId);
        eventBus.publish(new VehicleRetrievedEvent(tag.definitionId, ship));
        return true;
    }

    private int usedSlots(VehicleBayComponent bay) {
        int slots = 0;
        for (String id : bay.storedVehicleIds) {
            VehicleDefinition def = registry.get(id);
            slots += (def != null ? Math.max(1, def.baySlots) : 1);
        }
        return slots;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.VehicleBayServiceTest"`
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/VehicleBayService.java core/src/test/java/com/galacticodyssey/planet/terrain/VehicleBayServiceTest.java
git commit -m "feat(vehicle): add VehicleBayService for deploy/retrieve"
```

---

### Task 13: Extend `InteractionSystem` — enter / exit / retrieve

Adds vehicle handling to the existing E-key (`interactPressed`) flow. While `ON_FOOT_EXTERIOR`, if near a deployed vehicle's entry point, show `[F] Enter Vehicle` and on press switch to `DRIVING`. While `DRIVING`, show `[F] Exit Vehicle` and on press return to `ON_FOOT_EXTERIOR`; additionally, if the vehicle is within a ship bay's `triggerRadius`, offer retrieve.

This task wires the transitions; retrieve delegates to a `VehicleBayService` injected into `InteractionSystem`. Because `InteractionSystem` is GL-free (pure ECS + math), it is unit-testable.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/InteractionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/systems/InteractionVehicleTest.java`

- [ ] **Step 1: Read the current `InteractionSystem`**

Re-read `InteractionSystem.java`. Note: it queries `playerEntities` (first player), switches on `state.currentMode`, uses `freezePlayerBody`/`unfreezePlayerBody`/`teleportPlayer`/`toWorldSpace` helpers, and resets `input.interactPressed = false` at the end. You will add a `VehicleBayService` field (nullable; transitions that don't need it still work) and new mode branches.

- [ ] **Step 2: Write the failing test**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.PlayerEnterVehicleEvent;
import com.galacticodyssey.core.events.PlayerExitVehicleEvent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.planet.terrain.GroundVehicleComponent;
import com.galacticodyssey.planet.terrain.VehicleEntryPointComponent;
import com.galacticodyssey.planet.terrain.VehicleTagComponent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class InteractionVehicleTest {

    private Entity player(Vector3 pos) {
        Entity p = new Entity();
        p.add(new PlayerTagComponent());
        TransformComponent t = new TransformComponent();
        t.position.set(pos);
        p.add(t);
        p.add(new PlayerInputComponent());
        p.add(new PlayerStateComponent());
        // no PhysicsBodyComponent: freeze/unfreeze helpers must null-guard (they already do)
        return p;
    }

    private Entity vehicle(Vector3 pos) {
        Entity v = new Entity();
        v.add(new VehicleTagComponent());
        TransformComponent t = new TransformComponent();
        t.position.set(pos);
        v.add(t);
        v.add(new GroundVehicleComponent());
        VehicleEntryPointComponent entry = new VehicleEntryPointComponent();
        entry.triggerRadius = 3f;
        v.add(entry);
        return v;
    }

    @Test
    void pressingInteractNearVehicleEntersDriving() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicReference<PlayerEnterVehicleEvent> evt = new AtomicReference<>();
        bus.subscribe(PlayerEnterVehicleEvent.class, evt::set);
        InteractionSystem sys = new InteractionSystem(bus);
        engine.addSystem(sys);

        Entity p = player(new Vector3(0, 0, 0));
        Entity v = vehicle(new Vector3(1, 0, 0)); // within 3m
        engine.addEntity(p);
        engine.addEntity(v);

        p.getComponent(PlayerInputComponent.class).interactPressed = true;
        sys.update(1f / 60f);

        PlayerStateComponent st = p.getComponent(PlayerStateComponent.class);
        assertEquals(PlayerMode.DRIVING, st.currentMode);
        assertSame(v, st.currentVehicle);
        assertNotNull(evt.get());
    }

    @Test
    void pressingInteractWhileDrivingExits() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicReference<PlayerExitVehicleEvent> evt = new AtomicReference<>();
        bus.subscribe(PlayerExitVehicleEvent.class, evt::set);
        InteractionSystem sys = new InteractionSystem(bus);
        engine.addSystem(sys);

        Entity p = player(new Vector3(0, 0, 0));
        Entity v = vehicle(new Vector3(1, 0, 0));
        engine.addEntity(p);
        engine.addEntity(v);
        PlayerStateComponent st = p.getComponent(PlayerStateComponent.class);
        st.currentMode = PlayerMode.DRIVING;
        st.currentVehicle = v;

        p.getComponent(PlayerInputComponent.class).interactPressed = true;
        sys.update(1f / 60f);

        assertEquals(PlayerMode.ON_FOOT_EXTERIOR, st.currentMode);
        assertNull(st.currentVehicle);
        assertNotNull(evt.get());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.player.systems.InteractionVehicleTest"`
Expected: FAIL — compile error (no vehicle handling / no `currentVehicle` branch) or assertion failure.

- [ ] **Step 4: Wire vehicle handling into `InteractionSystem`**

Add imports at the top:
```java
import com.galacticodyssey.planet.terrain.GroundVehicleComponent;
import com.galacticodyssey.planet.terrain.VehicleBayService;
import com.galacticodyssey.planet.terrain.VehicleEntryPointComponent;
import com.galacticodyssey.planet.terrain.VehicleTagComponent;
import com.galacticodyssey.core.events.PlayerEnterVehicleEvent;
import com.galacticodyssey.core.events.PlayerExitVehicleEvent;
import com.badlogic.ashley.utils.ImmutableArray;
```

Add fields + mappers + an optional service setter:
```java
    private final ComponentMapper<VehicleEntryPointComponent> vehicleEntryMapper =
        ComponentMapper.getFor(VehicleEntryPointComponent.class);
    private ImmutableArray<Entity> vehicleEntities;
    private VehicleBayService bayService; // optional; set during GameWorld wiring

    public void setVehicleBayService(VehicleBayService bayService) { this.bayService = bayService; }
```

In `addedToEngine(...)`, register the vehicle family:
```java
        vehicleEntities = engine.getEntitiesFor(Family.all(
            VehicleTagComponent.class, VehicleEntryPointComponent.class, TransformComponent.class).get());
```

In the `update(...)` mode switch, extend the `ON_FOOT_EXTERIOR` case to also check vehicles, and add a `DRIVING` case:
```java
            case ON_FOOT_EXTERIOR:
                if (!checkNpcDialog(player, playerTransform, input)) {
                    if (!checkEnterVehicle(player, playerTransform, state, input)) {
                        checkShipEntry(player, playerTransform, state, input);
                    }
                }
                break;
            case ON_FOOT_INTERIOR:
                checkPilotSeat(player, state, input);
                checkShipExit(player, state, input);
                break;
            case PILOTING: checkStopPiloting(player, state, input); break;
            case DRIVING: checkExitVehicle(player, state, input); break;
```

Add the two new methods:
```java
    private boolean checkEnterVehicle(Entity player, TransformComponent playerTransform,
                                      PlayerStateComponent state, PlayerInputComponent input) {
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (int i = 0; i < vehicleEntities.size(); i++) {
            Entity v = vehicleEntities.get(i);
            TransformComponent vt = transformMapper.get(v);
            VehicleEntryPointComponent entry = vehicleEntryMapper.get(v);
            float dist = tempVec.set(playerTransform.position).dst(vt.position);
            if (dist < entry.triggerRadius && dist < nearestDist) {
                nearestDist = dist;
                nearest = v;
            }
        }
        if (nearest == null) return false;

        eventBus.publish(new InteractionPromptEvent("[F] Enter Vehicle", true));
        if (input.interactPressed) {
            state.currentMode = PlayerMode.DRIVING;
            state.currentVehicle = nearest;
            freezePlayerBody(player);
            eventBus.publish(new PlayerEnterVehicleEvent(player, nearest));
        }
        return true;
    }

    private void checkExitVehicle(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (state.currentVehicle == null) return;
        eventBus.publish(new InteractionPromptEvent("[F] Exit Vehicle", true));
        if (!input.interactPressed) return;

        Entity vehicle = state.currentVehicle;
        TransformComponent vt = transformMapper.get(vehicle);

        // Drop the player beside the vehicle, mirroring the pilot-exit pattern.
        state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
        state.currentVehicle = null;
        // Stop the vehicle coasting under the last driver input.
        GroundVehicleComponent gv = vehicle.getComponent(GroundVehicleComponent.class);
        if (gv != null) { gv.throttleInput = 0f; gv.steerInput = 0f; }

        unfreezePlayerBody(player);
        if (vt != null) {
            worldPos.set(vt.position).add(2f, 0f, 0f);
            teleportPlayer(player, worldPos);
        }
        eventBus.publish(new PlayerExitVehicleEvent(player, vehicle));
    }
```

(Retrieve-from-driving is handled at the bay panel / `VehicleBayService` level in the UI task; `checkExitVehicle` only drops the player to foot. The player then retrieves via the bay panel or by walking the ramp — consistent with the spec's "if driving at retrieval, first dropped to ON_FOOT_EXTERIOR".)

- [ ] **Step 5: Run test + the existing interaction/ship tests**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.player.systems.InteractionVehicleTest"`
Expected: PASS.
Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.player.systems.*" --tests "com.galacticodyssey.ship.systems.PilotingIntegrationTest"`
Expected: PASS (no regression to ship entry/pilot).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/InteractionSystem.java core/src/test/java/com/galacticodyssey/player/systems/InteractionVehicleTest.java
git commit -m "feat(vehicle): enter/exit vehicle transitions in InteractionSystem"
```

---

## Phase 5 — Camera, UI, rendering, wiring (GL / integration)

These tasks touch GL/Scene2D/`GameScreen` and the render loop, which the project does not unit-test. Verify them with a build + a manual run using the `run-galactic-odyssey` skill.

### Task 14: `VehicleCameraSystem` + guard `CameraSystem`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/VehicleCameraSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java`

- [ ] **Step 1: Inspect `ShipCameraSystem` CHASE mode and `CameraSystem`**

Read `core/src/main/java/com/galacticodyssey/ship/systems/ShipCameraSystem.java` (CHASE branch) and `CameraSystem.java`. Note how they obtain the `PerspectiveCamera`, how `ShipCameraSystem` lerps a chase position, and how `CameraSystem` early-returns for `PILOTING`. Mirror both.

- [ ] **Step 2: Guard `CameraSystem` for DRIVING**

In `CameraSystem.java`, find the early return for `PILOTING` and extend it:
```java
        if (playerState.currentMode == PlayerMode.PILOTING
                || playerState.currentMode == PlayerMode.DRIVING) return;
```

- [ ] **Step 3: Create `VehicleCameraSystem`**

Model it on `ShipCameraSystem`'s CHASE branch: when the player is `DRIVING`, place the camera behind+above `state.currentVehicle` (use the vehicle `TransformComponent` rotation for "behind"), lerp position/direction, look at the vehicle. Constructor takes the `PerspectiveCamera` (same as `ShipCameraSystem`). Gate with `if (state.currentMode != PlayerMode.DRIVING) return;`. Initialize the lerp target from the current camera pose on the first DRIVING frame (track a `boolean justEntered`) to avoid a snap.

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;

/** Chase camera for the driven vehicle. Active only while DRIVING. Mirrors ShipCameraSystem CHASE. */
public class VehicleCameraSystem extends EntitySystem {

    public static final int PRIORITY = 4;
    private static final float BACK = 9f, UP = 4f, LERP = 6f;

    private final PerspectiveCamera camera;
    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> players;
    private final Matrix4 tmpMat = new Matrix4();
    private final Quaternion tmpQuat = new Quaternion();
    private final Vector3 back = new Vector3();
    private final Vector3 desiredPos = new Vector3();
    private boolean active;

    public VehicleCameraSystem(PerspectiveCamera camera) {
        super(PRIORITY);
        this.camera = camera;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() == 0) { active = false; return; }
        Entity player = players.first();
        PlayerStateComponent state = STATE_M.get(player);
        if (state.currentMode != PlayerMode.DRIVING || state.currentVehicle == null) {
            active = false;
            return;
        }
        TransformComponent vt = TRANSFORM_M.get(state.currentVehicle);
        if (vt == null) return;

        tmpMat.set(vt.position, vt.rotation);
        tmpMat.getRotation(tmpQuat);
        back.set(0f, 0f, 1f).mul(tmpQuat).nor(); // +Z is "behind" (forward is -Z)
        desiredPos.set(vt.position).mulAdd(back, BACK).add(0f, UP, 0f);

        float a = active ? Math.min(1f, LERP * deltaTime) : 1f;
        camera.position.lerp(desiredPos, a);
        camera.lookAt(vt.position);
        camera.up.set(0f, 1f, 0f);
        camera.update();
        active = true;
    }
}
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/VehicleCameraSystem.java core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java
git commit -m "feat(vehicle): add VehicleCameraSystem chase cam + guard CameraSystem"
```

---

### Task 15: Vehicle rendering hook in `GameScreen`

The deferred renderer draws `ModelInstance`s collected in `GameScreen`. Add a hook that, on `VehicleDeployedEvent`, builds a `ModelInstance` for the vehicle (from `VehicleRenderComponent.modelPath`, falling back to the existing box model used elsewhere in `GameScreen`) and tracks it positioned by the vehicle's `TransformComponent`; on `VehicleRetrievedEvent`, removes it.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Inspect how `GameScreen` builds and draws box instances**

Read the `boxInstances` usage in `GameScreen.java` (the `ModelInstance instance = new ModelInstance(boxModel)` path around line 908 and where `boxInstances` is iterated during render). Mirror that for vehicles: keep a `Map<Entity, ModelInstance> vehicleInstances`, subscribe to `VehicleDeployedEvent`/`VehicleRetrievedEvent`, and each frame set each instance's transform from the vehicle `TransformComponent` (`PhysicsBodySystem` already syncs transform from the body), then add them to the opaque render list alongside `boxInstances`.

- [ ] **Step 2: Add the hook**

In `GameScreen`'s field section:
```java
    private final java.util.Map<com.badlogic.ashley.core.Entity, ModelInstance> vehicleInstances =
        new java.util.HashMap<>();
```
Where the event bus subscriptions are set up (where other `eventBus.subscribe(...)` calls live), add:
```java
        eventBus.subscribe(com.galacticodyssey.planet.terrain.events.VehicleDeployedEvent.class, e -> {
            ModelInstance mi = new ModelInstance(boxModel); // fallback primitive; swap for loaded model later
            vehicleInstances.put(e.vehicle, mi);
        });
        eventBus.subscribe(com.galacticodyssey.planet.terrain.events.VehicleRetrievedEvent.class, e -> {
            // VehicleRetrievedEvent carries only the def id; remove any instance whose entity was despawned.
            vehicleInstances.entrySet().removeIf(entry ->
                entry.getKey().getComponents().size() == 0);
        });
```
(Per architecture rule #3 the renderer reacts to events; it never drives simulation. If you prefer a precise removal key, add the `Entity` to `VehicleRetrievedEvent` — but the def-id form keeps the bay-service contract simple, and despawned Ashley entities report zero components.)

In the per-frame instance-update block (where `boxInstances` transforms are refreshed), add:
```java
        for (var entry : vehicleInstances.entrySet()) {
            com.galacticodyssey.core.components.TransformComponent t =
                entry.getKey().getComponent(com.galacticodyssey.core.components.TransformComponent.class);
            if (t != null) {
                entry.getValue().transform.set(t.position, t.rotation);
            }
        }
```
And include `vehicleInstances.values()` wherever `boxInstances` is submitted to the opaque renderer.

- [ ] **Step 3: Build**

Run: `.\gradlew.bat :desktop:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(vehicle): render deployed vehicles in GameScreen"
```

---

### Task 16: Register systems in `GameWorld` + load the registry

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Inspect existing registration**

Read `GameWorld.java` around the `surfaceVehicleSystem` creation (≈ line 292) and the combat-system registration (≈ lines 353–368). Note: `BulletPhysicsSystem` exposes `getDynamicsWorld()`; `EventBus` is available as `eventBus`; the `PerspectiveCamera` used by `ShipCameraSystem` is constructed/owned here.

- [ ] **Step 2: Construct and register the new pieces**

Near the other registry loads at bootstrap:
```java
        VehicleRegistry vehicleRegistry = new VehicleRegistry();
        vehicleRegistry.load("data/vehicles/vehicles.json");
```
Where systems are added (alongside `surfaceVehicleSystem`, `interactionSystem`, combat systems, and the camera systems):
```java
        // IMPORTANT ordering: VehicleControlSystem (priority 0) reads the player's per-frame
        // PlayerInputComponent and CombatInputComponent, so it must be added AFTER PlayerInputSystem
        // and CombatInputSystem (also priority 0 — Ashley breaks priority ties by insertion order) and
        // before WeaponSystem (priority 4). Add it here, after those input systems are registered.
        VehicleControlSystem vehicleControlSystem = new VehicleControlSystem();
        engine.addSystem(vehicleControlSystem);

        VehicleWeaponSystem vehicleWeaponSystem = new VehicleWeaponSystem(eventBus);
        engine.addSystem(vehicleWeaponSystem);

        VehicleCameraSystem vehicleCameraSystem = new VehicleCameraSystem(camera); // same camera as ShipCameraSystem
        engine.addSystem(vehicleCameraSystem);

        VehicleBayService vehicleBayService = new VehicleBayService(
            engine, bulletPhysicsSystem.getDynamicsWorld(),
            vehicleRegistry, new VehicleFactory(), eventBus);
        interactionSystem.setVehicleBayService(vehicleBayService);
```
(Use the actual local/field name for the `PerspectiveCamera` and `BulletPhysicsSystem` found in step 1. Expose `vehicleRegistry` and `vehicleBayService` as fields with getters if `GameScreen` needs them for the bay panel — see Task 17.)

- [ ] **Step 3: Build + run the full core test suite**

Run: `.\gradlew.bat :core:test`
Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 4: Manual smoke test**

Use the `run-galactic-odyssey` skill to launch the game. With a ship that has a `VehicleBayComponent` populated with `"rover_light"` (seed one in the test/dev scene where ships are spawned), confirm: deploy spawns a box vehicle by the ramp; walking up shows `[F] Enter Vehicle`; pressing F switches to chase cam; W/S drives, A/D steers; mouse fires (tracer/projectile + damage on a target); F exits to foot.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(vehicle): register vehicle systems and load VehicleRegistry"
```

---

### Task 17: `VehicleBayPanel` (Scene2D) + bay snapshot persistence

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/VehicleBayPanel.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/VehicleBaySnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/components/VehicleBayComponent.java` (implement `Snapshotable`)
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java`

- [ ] **Step 1: Inspect the Scene2D + snapshot patterns**

Read the crew-recruitment screen referenced in [docs/superpowers/specs/2026-05-28-crew-recruitment-screen-design.md](../specs/2026-05-28-crew-recruitment-screen-design.md) and its panel class for the Scene2D layout idiom (`Table`, `ScrollPane`, `TextButton`, skin usage). Read `persistence/snapshots/HealthSnapshot.java` and how `HealthComponent` implements `Snapshotable`, plus how `SnapshotComponentRegistry` registers each component type — mirror exactly.

- [ ] **Step 2: Implement bay snapshot persistence**

`VehicleBaySnapshot.java` (mirror `HealthSnapshot`'s plain-fields style):
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class VehicleBaySnapshot {
    public int capacity;
    public List<String> storedVehicleIds = new ArrayList<>();
    public float triggerRadius;
    public float rampX, rampY, rampZ;
}
```
Make `VehicleBayComponent implements Component, Snapshotable<VehicleBaySnapshot>`:
```java
    @Override
    public VehicleBaySnapshot takeSnapshot() {
        VehicleBaySnapshot s = new VehicleBaySnapshot();
        s.capacity = capacity;
        s.storedVehicleIds = new ArrayList<>(storedVehicleIds);
        s.triggerRadius = triggerRadius;
        s.rampX = localRampSpawnPosition.x;
        s.rampY = localRampSpawnPosition.y;
        s.rampZ = localRampSpawnPosition.z;
        return s;
    }

    @Override
    public void restoreFromSnapshot(VehicleBaySnapshot s) {
        capacity = s.capacity;
        storedVehicleIds.clear();
        storedVehicleIds.addAll(s.storedVehicleIds);
        triggerRadius = s.triggerRadius;
        localRampSpawnPosition.set(s.rampX, s.rampY, s.rampZ);
    }
```
Add the imports (`Snapshotable`, `VehicleBaySnapshot`, `java.util.ArrayList`). Register the component in `SnapshotComponentRegistry` following the existing one-line-per-component pattern (and add Kryo registration if `HealthSnapshot` requires it there — match whatever `HealthSnapshot`/`VehicleBaySnapshot` siblings do).

- [ ] **Step 3: Round-trip test for the snapshot**

Test: `core/src/test/java/com/galacticodyssey/ship/components/VehicleBaySnapshotTest.java`
```java
package com.galacticodyssey.ship.components;

import com.galacticodyssey.persistence.snapshots.VehicleBaySnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleBaySnapshotTest {
    @Test
    void roundTripsStoredVehicles() {
        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 3;
        bay.storedVehicleIds.add("rover_light");
        bay.storedVehicleIds.add("tank_medium");
        bay.localRampSpawnPosition.set(1, 2, 3);

        VehicleBaySnapshot s = bay.takeSnapshot();
        VehicleBayComponent restored = new VehicleBayComponent();
        restored.restoreFromSnapshot(s);

        assertEquals(3, restored.capacity);
        assertEquals(2, restored.storedVehicleIds.size());
        assertEquals("tank_medium", restored.storedVehicleIds.get(1));
        assertEquals(3f, restored.localRampSpawnPosition.z, 1e-4);
    }
}
```
Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.components.VehicleBaySnapshotTest"`
Expected: PASS.

- [ ] **Step 4: Create `VehicleBayPanel`**

A Scene2D `Table`-based panel (mirroring the crew-recruitment panel skin/layout) that takes the `VehicleBayComponent`, the current ship `Entity`, the `VehicleRegistry` (for display names/stats), and the `VehicleBayService`. It lists each stored vehicle with name + HP/armor/weapon summary and a "Deploy" `TextButton` that calls `bayService.deploy(ship, id)` and refreshes; it subscribes to `VehicleDeployedEvent`/`VehicleRetrievedEvent` to rebuild the list; it shows a capacity line (`usedSlots / capacity`). No simulation logic lives in the panel — it only issues deploy commands and reads state. Open it from the interaction prompt when the player is inside the vehicle bay room (wire the open trigger where other Scene2D panels are toggled in `GameScreen`).

(Exact widget code follows the crew-recruitment panel idiom; reproduce that structure with vehicle fields. Verify by building and the manual run in Task 16's smoke test extended to open the panel and click Deploy.)

- [ ] **Step 5: Build**

Run: `.\gradlew.bat :desktop:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/VehicleBayPanel.java core/src/main/java/com/galacticodyssey/persistence/snapshots/VehicleBaySnapshot.java core/src/main/java/com/galacticodyssey/ship/components/VehicleBayComponent.java core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java core/src/test/java/com/galacticodyssey/ship/components/VehicleBaySnapshotTest.java
git commit -m "feat(vehicle): add VehicleBayPanel UI and bay snapshot persistence"
```

---

## Final verification

- [ ] **Run the whole core suite:** `.\gradlew.bat :core:test` → all pass.
- [ ] **Build desktop:** `.\gradlew.bat :desktop:compileJava` → success.
- [ ] **Manual run** (`run-galactic-odyssey` skill): full loop — open bay panel, deploy, walk-in, drive, steer, fire on a target (confirm it takes damage), exit, retrieve. Confirm the ship HUD/FPS HUD swap correctly via the existing event subscribers and that exiting a vehicle leaves the player controllable on foot.
- [ ] **Update docs:** add a "Vehicle bay & deployment" entry to `docs/systems/` (mirror an existing system doc) and flip the `docs/TODO-systems.md` row to implemented. Commit.

---

## Notes on out-of-scope items (do NOT implement here)

- Persisting deployed-in-world vehicles across save/load (only bay contents persist).
- Networked replication of vehicles.
- Drop-pod / orbital deployment.
- Vehicle interiors / passenger seats / turret gunners.
- "Landed on surface" gating (ramp-deployed gating only — and for MVP the bay panel can deploy whenever opened).
