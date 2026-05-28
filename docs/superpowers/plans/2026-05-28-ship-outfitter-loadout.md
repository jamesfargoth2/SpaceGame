# Ship Outfitter / Loadout Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a ship-centric outfitter screen where players customize weapons, internal modules, and (stubbed) cosmetics on their ship, with power/mass budget constraints, stat comparison, and drag-and-drop interaction.

**Architecture:** New data layer (ShipModuleData, ShipModuleSlot, ShipLoadoutComponent, ShipCargoComponent) mirrors the existing hardpoint/weapon pattern. A new OutfitterScreenSystem (following the InventoryScreenSystem pattern) manages the overlay UI with Scene2D actors. An OutfitterSystem ECS system recalculates ship stats on loadout changes. All module/slot definitions are JSON-driven.

**Tech Stack:** Java 17, libGDX Scene2D.UI, Ashley ECS, libGDX JsonReader for data loading, JUnit 5 for tests.

**Spec:** `docs/superpowers/specs/2026-05-28-ship-outfitter-loadout-design.md`

---

## File Structure

### New Files — Data Layer
- `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleCategory.java` — enum for module categories
- `core/src/main/java/com/galacticodyssey/ship/modules/ModuleSlotType.java` — enum for slot types (REACTOR, ENGINE, INTERNAL)
- `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleData.java` — module definition POJO
- `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleSlot.java` — slot instance (analogous to Hardpoint)
- `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleRegistry.java` — loads modules from JSON
- `core/src/main/java/com/galacticodyssey/ship/modules/components/ShipLoadoutComponent.java` — ECS component for module slots
- `core/src/main/java/com/galacticodyssey/ship/modules/components/ShipCargoComponent.java` — ECS component for stored ship equipment
- `core/src/main/resources/data/modules/ship_modules.json` — module definitions
- `core/src/main/resources/data/modules/ship_module_slots.json` — per-ship-class slot layouts

### New Files — Events
- `core/src/main/java/com/galacticodyssey/ship/modules/events/ModuleInstalledEvent.java`
- `core/src/main/java/com/galacticodyssey/ship/modules/events/ModuleUninstalledEvent.java`
- `core/src/main/java/com/galacticodyssey/ui/events/OutfitterOpenedEvent.java`
- `core/src/main/java/com/galacticodyssey/ui/events/OutfitterClosedEvent.java`

### New Files — System
- `core/src/main/java/com/galacticodyssey/ship/modules/systems/OutfitterSystem.java` — ECS system that recalculates ship stats on loadout changes

### New Files — UI
- `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterScreenSystem.java` — main controller (mirrors InventoryScreenSystem)
- `core/src/main/java/com/galacticodyssey/ui/outfitter/ShipSilhouetteActor.java` — center ship view with clickable slots
- `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterInventoryPanel.java` — left panel with cargo/station tabs
- `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterDetailPanel.java` — right panel with stat comparison
- `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterBudgetBar.java` — bottom budget gauges

### New Files — Tests
- `core/src/test/java/com/galacticodyssey/ship/modules/ShipModuleRegistryTest.java`
- `core/src/test/java/com/galacticodyssey/ship/modules/ShipLoadoutComponentTest.java`
- `core/src/test/java/com/galacticodyssey/ship/modules/ShipCargoComponentTest.java`
- `core/src/test/java/com/galacticodyssey/ship/modules/OutfitterSystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/modules/OutfitterValidationTest.java`

### Modified Files
- `core/src/main/java/com/galacticodyssey/ship/weapons/data/ShipWeaponData.java` — add `powerDraw` field
- `core/src/main/resources/data/weapons/ship_weapons.json` — add `powerDraw` values to all weapons
- `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java` — populate ShipLoadoutComponent and ShipCargoComponent
- `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` — wire OutfitterScreenSystem
- `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java` — register new components

---

## Task 1: Module Enums and Data Class

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleCategory.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/ModuleSlotType.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleData.java`

- [ ] **Step 1: Create ShipModuleCategory enum**

```java
package com.galacticodyssey.ship.modules;

public enum ShipModuleCategory {
    REACTOR,
    ENGINE,
    SHIELD_GENERATOR,
    ARMOR_PLATING,
    CARGO_EXPANDER,
    SCANNER,
    ECM,
    REPAIR_DRONE,
    MINING_LASER,
    TRACTOR_BEAM
}
```

- [ ] **Step 2: Create ModuleSlotType enum**

```java
package com.galacticodyssey.ship.modules;

public enum ModuleSlotType {
    REACTOR,
    ENGINE,
    INTERNAL
}
```

- [ ] **Step 3: Create ShipModuleData class**

Mirrors the `ShipWeaponData` pattern — plain POJO with public fields:

```java
package com.galacticodyssey.ship.modules;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;

import java.util.HashMap;
import java.util.Map;

public class ShipModuleData {
    public String id;
    public String name;
    public String description;
    public ShipModuleCategory category;
    public HardpointSize size;
    public float powerDraw;
    public float mass;
    public Map<String, Float> stats = new HashMap<>();
    public QualityTier qualityTier = QualityTier.COMMON;
    public int price;

    public ShipModuleData() {}

    public ShipModuleData(ShipModuleData source) {
        this.id = source.id;
        this.name = source.name;
        this.description = source.description;
        this.category = source.category;
        this.size = source.size;
        this.powerDraw = source.powerDraw;
        this.mass = source.mass;
        this.stats = new HashMap<>(source.stats);
        this.qualityTier = source.qualityTier;
        this.price = source.price;
    }

    public boolean isReactor() { return category == ShipModuleCategory.REACTOR; }
    public float getPowerGeneration() { return isReactor() ? Math.abs(powerDraw) : 0f; }
}
```

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleCategory.java core/src/main/java/com/galacticodyssey/ship/modules/ModuleSlotType.java core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleData.java
git commit -m "feat(outfitter): add ShipModuleCategory, ModuleSlotType, ShipModuleData"
```

---

## Task 2: ShipModuleSlot

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleSlot.java`

- [ ] **Step 1: Create ShipModuleSlot class**

Analogous to `Hardpoint` — holds slot metadata and the currently installed module:

```java
package com.galacticodyssey.ship.modules;

import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;

public class ShipModuleSlot {
    public final String id;
    public final ModuleSlotType slotType;
    public final HardpointSize size;
    public final Vector2 position = new Vector2();
    public final boolean mandatory;
    public ShipModuleData installedModule;

    public ShipModuleSlot(String id, ModuleSlotType slotType, HardpointSize size, boolean mandatory) {
        this.id = id;
        this.slotType = slotType;
        this.size = size;
        this.mandatory = mandatory;
    }

    public boolean isEmpty() { return installedModule == null; }

    public boolean accepts(ShipModuleData module) {
        if (module == null) return false;
        if (module.size.ordinal() > size.ordinal()) return false;
        switch (slotType) {
            case REACTOR:  return module.category == ShipModuleCategory.REACTOR;
            case ENGINE:   return module.category == ShipModuleCategory.ENGINE;
            case INTERNAL: return module.category != ShipModuleCategory.REACTOR
                                && module.category != ShipModuleCategory.ENGINE;
            default:       return false;
        }
    }
}
```

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleSlot.java
git commit -m "feat(outfitter): add ShipModuleSlot with size and category validation"
```

---

## Task 3: ShipLoadoutComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/components/ShipLoadoutComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/modules/ShipLoadoutComponentTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.modules;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipLoadoutComponentTest {

    private ShipLoadoutComponent loadout;

    @BeforeEach
    void setUp() {
        loadout = new ShipLoadoutComponent();
        loadout.maxMass = 30f;

        ShipModuleSlot reactor = new ShipModuleSlot("reactor_0", ModuleSlotType.REACTOR, HardpointSize.SMALL, true);
        ShipModuleSlot engine = new ShipModuleSlot("engine_0", ModuleSlotType.ENGINE, HardpointSize.SMALL, true);
        ShipModuleSlot internal = new ShipModuleSlot("internal_0", ModuleSlotType.INTERNAL, HardpointSize.SMALL, false);
        loadout.moduleSlots.add(reactor);
        loadout.moduleSlots.add(engine);
        loadout.moduleSlots.add(internal);
    }

    @Test
    void getSlot_returnsMatchingSlot() {
        assertNotNull(loadout.getSlot("reactor_0"));
        assertNull(loadout.getSlot("nonexistent"));
    }

    @Test
    void getSlotsOfType_filtersCorrectly() {
        assertEquals(1, loadout.getSlotsOfType(ModuleSlotType.REACTOR).size());
        assertEquals(1, loadout.getSlotsOfType(ModuleSlotType.INTERNAL).size());
    }

    @Test
    void powerBudget_sumsCorrectly() {
        ShipModuleData reactor = new ShipModuleData();
        reactor.id = "reactor_mk1";
        reactor.category = ShipModuleCategory.REACTOR;
        reactor.powerDraw = -80f;
        reactor.mass = 5f;
        loadout.getSlot("reactor_0").installedModule = reactor;

        ShipModuleData engine = new ShipModuleData();
        engine.id = "engine_mk1";
        engine.category = ShipModuleCategory.ENGINE;
        engine.powerDraw = 20f;
        engine.mass = 4f;
        loadout.getSlot("engine_0").installedModule = engine;

        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_mk1";
        shield.category = ShipModuleCategory.SHIELD_GENERATOR;
        shield.powerDraw = 15f;
        shield.mass = 3f;
        loadout.getSlot("internal_0").installedModule = shield;

        assertEquals(80f, loadout.getTotalPowerGeneration(), 0.01f);
        assertEquals(35f, loadout.getTotalPowerDraw(), 0.01f);
        assertEquals(45f, loadout.getAvailablePower(), 0.01f);
        assertEquals(12f, loadout.getTotalModuleMass(), 0.01f);
    }

    @Test
    void powerBudget_emptySlots_returnZero() {
        assertEquals(0f, loadout.getTotalPowerGeneration(), 0.01f);
        assertEquals(0f, loadout.getTotalPowerDraw(), 0.01f);
        assertEquals(0f, loadout.getAvailablePower(), 0.01f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.ShipLoadoutComponentTest" --info`
Expected: Compilation failure — `ShipLoadoutComponent` does not exist yet.

- [ ] **Step 3: Write ShipLoadoutComponent**

```java
package com.galacticodyssey.ship.modules.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.modules.ModuleSlotType;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;

import java.util.ArrayList;
import java.util.List;

public class ShipLoadoutComponent implements Component {
    public final List<ShipModuleSlot> moduleSlots = new ArrayList<>();
    public float maxMass = 30f;

    public ShipModuleSlot getSlot(String id) {
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.id.equals(id)) return slot;
        }
        return null;
    }

    public List<ShipModuleSlot> getSlotsOfType(ModuleSlotType type) {
        List<ShipModuleSlot> result = new ArrayList<>();
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.slotType == type) result.add(slot);
        }
        return result;
    }

    public float getTotalPowerGeneration() {
        float gen = 0f;
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.installedModule != null && slot.installedModule.powerDraw < 0) {
                gen += Math.abs(slot.installedModule.powerDraw);
            }
        }
        return gen;
    }

    public float getTotalPowerDraw() {
        float draw = 0f;
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.installedModule != null && slot.installedModule.powerDraw > 0) {
                draw += slot.installedModule.powerDraw;
            }
        }
        return draw;
    }

    public float getAvailablePower() {
        return getTotalPowerGeneration() - getTotalPowerDraw();
    }

    public float getTotalModuleMass() {
        float mass = 0f;
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.installedModule != null) {
                mass += slot.installedModule.mass;
            }
        }
        return mass;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.ShipLoadoutComponentTest" --info`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/components/ShipLoadoutComponent.java core/src/test/java/com/galacticodyssey/ship/modules/ShipLoadoutComponentTest.java
git commit -m "feat(outfitter): add ShipLoadoutComponent with power/mass budget calculations"
```

---

## Task 4: ShipCargoComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/components/ShipCargoComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/modules/ShipCargoComponentTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.modules;

import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipCargoComponentTest {

    private ShipCargoComponent cargo;

    @BeforeEach
    void setUp() {
        cargo = new ShipCargoComponent();
    }

    @Test
    void addAndRemoveModule() {
        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_mk1";
        shield.mass = 3f;

        cargo.addModule(shield);
        assertEquals(1, cargo.storedModules.size());
        assertEquals(3f, cargo.getStoredMass(), 0.01f);

        assertTrue(cargo.removeModule(shield));
        assertEquals(0, cargo.storedModules.size());
        assertEquals(0f, cargo.getStoredMass(), 0.01f);
    }

    @Test
    void addAndRemoveWeapon() {
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "autocannon_sm";

        cargo.addWeapon(weapon);
        assertEquals(1, cargo.storedWeapons.size());

        assertTrue(cargo.removeWeapon(weapon));
        assertEquals(0, cargo.storedWeapons.size());
    }

    @Test
    void removeNonexistent_returnsFalse() {
        ShipModuleData mod = new ShipModuleData();
        mod.id = "nothing";
        assertFalse(cargo.removeModule(mod));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.ShipCargoComponentTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Write ShipCargoComponent**

```java
package com.galacticodyssey.ship.modules.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;

import java.util.ArrayList;
import java.util.List;

public class ShipCargoComponent implements Component {
    public final List<ShipWeaponData> storedWeapons = new ArrayList<>();
    public final List<ShipModuleData> storedModules = new ArrayList<>();

    public void addWeapon(ShipWeaponData weapon) { storedWeapons.add(weapon); }
    public void addModule(ShipModuleData module) { storedModules.add(module); }

    public boolean removeWeapon(ShipWeaponData weapon) { return storedWeapons.remove(weapon); }
    public boolean removeModule(ShipModuleData module) { return storedModules.remove(module); }

    public float getStoredMass() {
        float mass = 0f;
        for (ShipModuleData mod : storedModules) mass += mod.mass;
        return mass;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.ShipCargoComponentTest" --info`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/components/ShipCargoComponent.java core/src/test/java/com/galacticodyssey/ship/modules/ShipCargoComponentTest.java
git commit -m "feat(outfitter): add ShipCargoComponent for storing uninstalled ship equipment"
```

---

## Task 5: JSON Data Files

**Files:**
- Create: `core/src/main/resources/data/modules/ship_modules.json`
- Create: `core/src/main/resources/data/modules/ship_module_slots.json`
- Modify: `core/src/main/resources/data/weapons/ship_weapons.json` — add `powerDraw` field
- Modify: `core/src/main/java/com/galacticodyssey/ship/weapons/data/ShipWeaponData.java` — add `powerDraw` field

- [ ] **Step 1: Add powerDraw field to ShipWeaponData**

In `ShipWeaponData.java`, add after the `heatPerShot` field:

```java
public float powerDraw;  // continuous MW draw for tracking/cooling/standby
```

- [ ] **Step 2: Add powerDraw to ship_weapons.json**

Add `"powerDraw"` to each weapon entry. Read the file first, then add the field to each weapon. Values scale with weapon size:

| Weapon | powerDraw |
|--------|-----------|
| Light Autocannon | 2.0 |
| Plasma Turret | 5.0 |
| Missile Launcher | 3.0 |
| Railgun | 8.0 |
| Point Defense Laser | 3.0 |
| Flak Battery | 4.0 |
| EMP Projector | 6.0 |
| Heavy Laser | 7.0 |

- [ ] **Step 3: Update ShipWeaponRegistry to load powerDraw**

In `ShipWeaponRegistry.loadWeapons()`, add after the `heatPerShot` line:

```java
data.powerDraw = entry.getFloat("powerDraw", 0f);
```

- [ ] **Step 4: Create ship_modules.json**

```json
[
  {
    "id": "reactor_compact_sm",
    "name": "Compact Fusion Reactor",
    "description": "A small, reliable fusion reactor suitable for light craft.",
    "category": "REACTOR",
    "size": "SMALL",
    "powerDraw": -60.0,
    "mass": 4.0,
    "stats": {},
    "qualityTier": "COMMON",
    "price": 5000
  },
  {
    "id": "reactor_standard_md",
    "name": "Standard Fusion Reactor",
    "description": "Medium reactor providing solid power output.",
    "category": "REACTOR",
    "size": "MEDIUM",
    "powerDraw": -120.0,
    "mass": 8.0,
    "stats": {},
    "qualityTier": "COMMON",
    "price": 15000
  },
  {
    "id": "engine_ion_sm",
    "name": "Ion Drive",
    "description": "Efficient ion thruster for light ships.",
    "category": "ENGINE",
    "size": "SMALL",
    "powerDraw": 15.0,
    "mass": 3.0,
    "stats": { "thrustMultiplier": 1.0, "turnRateMultiplier": 1.0, "maxSpeedBonus": 0.0 },
    "qualityTier": "COMMON",
    "price": 4000
  },
  {
    "id": "engine_plasma_md",
    "name": "Plasma Drive",
    "description": "Powerful plasma thruster with high acceleration.",
    "category": "ENGINE",
    "size": "MEDIUM",
    "powerDraw": 30.0,
    "mass": 6.0,
    "stats": { "thrustMultiplier": 1.3, "turnRateMultiplier": 1.1, "maxSpeedBonus": 15.0 },
    "qualityTier": "REFINED",
    "price": 12000
  },
  {
    "id": "shield_gen_sm",
    "name": "Light Shield Generator",
    "description": "Basic energy shield for light craft.",
    "category": "SHIELD_GENERATOR",
    "size": "SMALL",
    "powerDraw": 10.0,
    "mass": 2.0,
    "stats": { "shieldHp": 200.0, "shieldRechargeRate": 5.0 },
    "qualityTier": "COMMON",
    "price": 6000
  },
  {
    "id": "shield_gen_md",
    "name": "Military Shield Generator",
    "description": "Reinforced shield generator with fast recharge.",
    "category": "SHIELD_GENERATOR",
    "size": "MEDIUM",
    "powerDraw": 20.0,
    "mass": 4.0,
    "stats": { "shieldHp": 500.0, "shieldRechargeRate": 12.0 },
    "qualityTier": "MILITARY",
    "price": 18000
  },
  {
    "id": "armor_plate_sm",
    "name": "Composite Armor Plating",
    "description": "Layered composite hull reinforcement.",
    "category": "ARMOR_PLATING",
    "size": "SMALL",
    "powerDraw": 0.0,
    "mass": 5.0,
    "stats": { "ballisticResist": 0.15, "energyResist": 0.05 },
    "qualityTier": "COMMON",
    "price": 3000
  },
  {
    "id": "cargo_expander_sm",
    "name": "Cargo Rack",
    "description": "Additional cargo storage framework.",
    "category": "CARGO_EXPANDER",
    "size": "SMALL",
    "powerDraw": 0.0,
    "mass": 1.0,
    "stats": { "cargoCapacity": 50.0 },
    "qualityTier": "COMMON",
    "price": 2000
  },
  {
    "id": "scanner_basic_sm",
    "name": "Survey Scanner",
    "description": "Basic scanner for resource and threat detection.",
    "category": "SCANNER",
    "size": "SMALL",
    "powerDraw": 5.0,
    "mass": 1.0,
    "stats": { "scanRange": 2000.0, "scanResolution": 1.0 },
    "qualityTier": "COMMON",
    "price": 4000
  },
  {
    "id": "ecm_basic_sm",
    "name": "ECM Suite",
    "description": "Electronic countermeasures to reduce sensor signature.",
    "category": "ECM",
    "size": "SMALL",
    "powerDraw": 8.0,
    "mass": 1.5,
    "stats": { "signatureReduction": 0.2 },
    "qualityTier": "REFINED",
    "price": 8000
  },
  {
    "id": "repair_drone_sm",
    "name": "Repair Drone Bay",
    "description": "Deploys micro-drones for slow passive hull repair.",
    "category": "REPAIR_DRONE",
    "size": "SMALL",
    "powerDraw": 6.0,
    "mass": 2.0,
    "stats": { "repairRate": 2.0 },
    "qualityTier": "REFINED",
    "price": 10000
  },
  {
    "id": "mining_laser_sm",
    "name": "Mining Laser",
    "description": "Focused laser for asteroid and surface resource extraction.",
    "category": "MINING_LASER",
    "size": "SMALL",
    "powerDraw": 12.0,
    "mass": 2.5,
    "stats": { "miningRate": 1.0, "miningRange": 500.0 },
    "qualityTier": "COMMON",
    "price": 7000
  },
  {
    "id": "tractor_beam_sm",
    "name": "Tractor Beam",
    "description": "Gravity manipulator for pulling objects and salvage.",
    "category": "TRACTOR_BEAM",
    "size": "SMALL",
    "powerDraw": 10.0,
    "mass": 2.0,
    "stats": { "tractorRange": 300.0, "tractorForce": 5000.0 },
    "qualityTier": "COMMON",
    "price": 9000
  }
]
```

- [ ] **Step 5: Create ship_module_slots.json**

```json
{
  "corvette_scout": {
    "maxMass": 30.0,
    "silhouettePoints": [[100,10],[130,60],[140,120],[145,200],[135,280],[120,320],[100,330],[80,320],[65,280],[55,200],[60,120],[70,60]],
    "wingPoints": [
      [[55,180],[30,200],[28,240],[55,230]],
      [[145,180],[170,200],[172,240],[145,230]]
    ],
    "engineGlows": [[85,325],[115,325]],
    "moduleSlots": [
      { "id": "reactor_0", "slotType": "REACTOR", "size": "SMALL", "position": [100, 140], "mandatory": true, "defaultModuleId": "reactor_compact_sm" },
      { "id": "engine_0", "slotType": "ENGINE", "size": "SMALL", "position": [100, 270], "mandatory": true, "defaultModuleId": "engine_ion_sm" },
      { "id": "internal_0", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 175] },
      { "id": "internal_1", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 210] },
      { "id": "internal_2", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 245] }
    ]
  },
  "frigate_patrol": {
    "maxMass": 80.0,
    "silhouettePoints": [[100,5],[140,50],[155,110],[160,200],[150,300],[130,350],[100,360],[70,350],[50,300],[40,200],[45,110],[60,50]],
    "wingPoints": [
      [[40,170],[15,195],[12,260],[40,245]],
      [[160,170],[185,195],[188,260],[160,245]]
    ],
    "engineGlows": [[75,355],[100,358],[125,355]],
    "moduleSlots": [
      { "id": "reactor_0", "slotType": "REACTOR", "size": "MEDIUM", "position": [100, 130], "mandatory": true, "defaultModuleId": "reactor_standard_md" },
      { "id": "engine_0", "slotType": "ENGINE", "size": "MEDIUM", "position": [100, 300], "mandatory": true, "defaultModuleId": "engine_plasma_md" },
      { "id": "internal_0", "slotType": "INTERNAL", "size": "MEDIUM", "position": [100, 170] },
      { "id": "internal_1", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 200] },
      { "id": "internal_2", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 230] },
      { "id": "internal_3", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 260] }
    ]
  }
}
```

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/weapons/data/ShipWeaponData.java core/src/main/resources/data/weapons/ship_weapons.json core/src/main/java/com/galacticodyssey/ship/weapons/data/ShipWeaponRegistry.java core/src/main/resources/data/modules/ship_modules.json core/src/main/resources/data/modules/ship_module_slots.json
git commit -m "feat(outfitter): add module JSON data, weapon powerDraw field, slot layouts"
```

---

## Task 6: ShipModuleRegistry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/modules/ShipModuleRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.modules;

import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipModuleRegistryTest {

    private ShipModuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShipModuleRegistry();
        registry.loadModules("data/modules/ship_modules.json");
        registry.loadSlotLayouts("data/modules/ship_module_slots.json");
    }

    @Test
    void loadModules_populatesRegistry() {
        assertNotNull(registry.getModule("reactor_compact_sm"));
        assertNotNull(registry.getModule("engine_ion_sm"));
        assertNotNull(registry.getModule("shield_gen_sm"));
    }

    @Test
    void getModule_returnsNullForUnknown() {
        assertNull(registry.getModule("nonexistent"));
    }

    @Test
    void createModuleInstance_returnsDeepCopy() {
        ShipModuleData original = registry.getModule("reactor_compact_sm");
        ShipModuleData copy = registry.createModuleInstance("reactor_compact_sm");
        assertNotNull(copy);
        assertNotSame(original, copy);
        assertEquals(original.id, copy.id);
        assertEquals(original.powerDraw, copy.powerDraw);
    }

    @Test
    void getModulesByCategory_filters() {
        List<ShipModuleData> reactors = registry.getModulesByCategory(ShipModuleCategory.REACTOR);
        assertTrue(reactors.size() >= 2);
        for (ShipModuleData mod : reactors) {
            assertEquals(ShipModuleCategory.REACTOR, mod.category);
        }
    }

    @Test
    void getModulesForSize_includesSmallerModules() {
        List<ShipModuleData> mediumFit = registry.getModulesForSize(HardpointSize.MEDIUM);
        boolean hasSmall = false;
        boolean hasMedium = false;
        for (ShipModuleData mod : mediumFit) {
            if (mod.size == HardpointSize.SMALL) hasSmall = true;
            if (mod.size == HardpointSize.MEDIUM) hasMedium = true;
        }
        assertTrue(hasSmall);
        assertTrue(hasMedium);
    }

    @Test
    void loadSlotLayouts_parsesCorvetteSlots() {
        ShipModuleRegistry.SlotLayout layout = registry.getSlotLayout("corvette_scout");
        assertNotNull(layout);
        assertEquals(30f, layout.maxMass, 0.01f);
        assertEquals(5, layout.slots.size());
        assertNotNull(layout.silhouettePoints);
        assertTrue(layout.silhouettePoints.length > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.ShipModuleRegistryTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Write ShipModuleRegistry**

```java
package com.galacticodyssey.ship.modules;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShipModuleRegistry {

    private final Map<String, ShipModuleData> modules = new HashMap<>();
    private final Map<String, SlotLayout> slotLayouts = new HashMap<>();

    public void loadModules(String path) {
        JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ShipModuleData data = new ShipModuleData();
            data.id = entry.getString("id");
            data.name = entry.getString("name");
            data.description = entry.getString("description", "");
            data.category = ShipModuleCategory.valueOf(entry.getString("category"));
            data.size = HardpointSize.valueOf(entry.getString("size"));
            data.powerDraw = entry.getFloat("powerDraw");
            data.mass = entry.getFloat("mass");
            data.qualityTier = QualityTier.valueOf(entry.getString("qualityTier", "COMMON"));
            data.price = entry.getInt("price", 0);

            JsonValue stats = entry.get("stats");
            if (stats != null) {
                for (JsonValue s = stats.child; s != null; s = s.next) {
                    data.stats.put(s.name, s.asFloat());
                }
            }
            modules.put(data.id, data);
        }
    }

    public void loadSlotLayouts(String path) {
        JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
        for (JsonValue ship = root.child; ship != null; ship = ship.next) {
            String shipClassId = ship.name;
            SlotLayout layout = new SlotLayout();
            layout.maxMass = ship.getFloat("maxMass");

            JsonValue pts = ship.get("silhouettePoints");
            if (pts != null) {
                layout.silhouettePoints = new float[pts.size * 2];
                int i = 0;
                for (JsonValue pt = pts.child; pt != null; pt = pt.next) {
                    layout.silhouettePoints[i++] = pt.get(0).asFloat();
                    layout.silhouettePoints[i++] = pt.get(1).asFloat();
                }
            }

            JsonValue wings = ship.get("wingPoints");
            if (wings != null) {
                layout.wingPolygons = new ArrayList<>();
                for (JsonValue wing = wings.child; wing != null; wing = wing.next) {
                    float[] wPts = new float[wing.size * 2];
                    int wi = 0;
                    for (JsonValue wp = wing.child; wp != null; wp = wp.next) {
                        wPts[wi++] = wp.get(0).asFloat();
                        wPts[wi++] = wp.get(1).asFloat();
                    }
                    layout.wingPolygons.add(wPts);
                }
            }

            JsonValue engines = ship.get("engineGlows");
            if (engines != null) {
                layout.engineGlows = new float[engines.size * 2];
                int ei = 0;
                for (JsonValue eg = engines.child; eg != null; eg = eg.next) {
                    layout.engineGlows[ei++] = eg.get(0).asFloat();
                    layout.engineGlows[ei++] = eg.get(1).asFloat();
                }
            }

            JsonValue slotsArr = ship.get("moduleSlots");
            for (JsonValue sl = slotsArr.child; sl != null; sl = sl.next) {
                SlotTemplate t = new SlotTemplate();
                t.id = sl.getString("id");
                t.slotType = ModuleSlotType.valueOf(sl.getString("slotType"));
                t.size = HardpointSize.valueOf(sl.getString("size"));
                t.mandatory = sl.getBoolean("mandatory", false);
                JsonValue pos = sl.get("position");
                t.posX = pos.get(0).asFloat();
                t.posY = pos.get(1).asFloat();
                t.defaultModuleId = sl.getString("defaultModuleId", null);
                layout.slots.add(t);
            }

            slotLayouts.put(shipClassId, layout);
        }
    }

    public ShipModuleData getModule(String id) { return modules.get(id); }

    public ShipModuleData createModuleInstance(String id) {
        ShipModuleData src = modules.get(id);
        return src != null ? new ShipModuleData(src) : null;
    }

    public List<ShipModuleData> getModulesByCategory(ShipModuleCategory category) {
        List<ShipModuleData> result = new ArrayList<>();
        for (ShipModuleData mod : modules.values()) {
            if (mod.category == category) result.add(mod);
        }
        return result;
    }

    public List<ShipModuleData> getModulesForSize(HardpointSize maxSize) {
        List<ShipModuleData> result = new ArrayList<>();
        for (ShipModuleData mod : modules.values()) {
            if (mod.size.ordinal() <= maxSize.ordinal()) result.add(mod);
        }
        return result;
    }

    public void registerModule(ShipModuleData data) { modules.put(data.id, data); }

    public SlotLayout getSlotLayout(String shipClassId) { return slotLayouts.get(shipClassId); }

    public static class SlotLayout {
        public float maxMass;
        public float[] silhouettePoints;
        public List<float[]> wingPolygons;
        public float[] engineGlows;
        public final List<SlotTemplate> slots = new ArrayList<>();
    }

    public static class SlotTemplate {
        public String id;
        public ModuleSlotType slotType;
        public HardpointSize size;
        public boolean mandatory;
        public float posX, posY;
        public String defaultModuleId;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.ShipModuleRegistryTest" --info`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/ShipModuleRegistry.java core/src/test/java/com/galacticodyssey/ship/modules/ShipModuleRegistryTest.java
git commit -m "feat(outfitter): add ShipModuleRegistry with JSON loading and slot layouts"
```

---

## Task 7: Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/events/ModuleInstalledEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/events/ModuleUninstalledEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/events/OutfitterOpenedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/events/OutfitterClosedEvent.java`

- [ ] **Step 1: Create all four event classes**

`ModuleInstalledEvent.java`:
```java
package com.galacticodyssey.ship.modules.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.ShipModuleData;

public final class ModuleInstalledEvent {
    public final Entity shipEntity;
    public final String slotId;
    public final ShipModuleData module;
    public final ShipModuleData previousModule;

    public ModuleInstalledEvent(Entity shipEntity, String slotId,
                                ShipModuleData module, ShipModuleData previousModule) {
        this.shipEntity = shipEntity;
        this.slotId = slotId;
        this.module = module;
        this.previousModule = previousModule;
    }
}
```

`ModuleUninstalledEvent.java`:
```java
package com.galacticodyssey.ship.modules.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.ShipModuleData;

public final class ModuleUninstalledEvent {
    public final Entity shipEntity;
    public final String slotId;
    public final ShipModuleData module;

    public ModuleUninstalledEvent(Entity shipEntity, String slotId, ShipModuleData module) {
        this.shipEntity = shipEntity;
        this.slotId = slotId;
        this.module = module;
    }
}
```

`OutfitterOpenedEvent.java`:
```java
package com.galacticodyssey.ui.events;

public final class OutfitterOpenedEvent {
    public final boolean stationMode;

    public OutfitterOpenedEvent(boolean stationMode) {
        this.stationMode = stationMode;
    }
}
```

`OutfitterClosedEvent.java`:
```java
package com.galacticodyssey.ui.events;

public final class OutfitterClosedEvent {}
```

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/events/ core/src/main/java/com/galacticodyssey/ui/events/OutfitterOpenedEvent.java core/src/main/java/com/galacticodyssey/ui/events/OutfitterClosedEvent.java
git commit -m "feat(outfitter): add outfitter and module install/uninstall events"
```

---

## Task 8: OutfitterSystem (Stat Recalculation)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/systems/OutfitterSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/modules/OutfitterSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.modules;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.modules.events.ModuleInstalledEvent;
import com.galacticodyssey.ship.modules.systems.OutfitterSystem;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutfitterSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private OutfitterSystem system;
    private Entity ship;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        system = new OutfitterSystem(eventBus);
        engine.addSystem(system);

        ship = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.mass = 8000f;
        data.maxThrust = 50000f;
        data.maxTurnRate = 90f;
        data.maxSpeed = 150f;
        ship.add(data);

        ShipLoadoutComponent loadout = new ShipLoadoutComponent();
        loadout.maxMass = 30f;
        loadout.moduleSlots.add(new ShipModuleSlot("reactor_0", ModuleSlotType.REACTOR, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("engine_0", ModuleSlotType.ENGINE, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("internal_0", ModuleSlotType.INTERNAL, HardpointSize.SMALL, false));
        ship.add(loadout);

        engine.addEntity(ship);
    }

    @Test
    void moduleInstalled_recalculatesShipMass() {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        ShipModuleData reactor = new ShipModuleData();
        reactor.id = "reactor_mk1";
        reactor.category = ShipModuleCategory.REACTOR;
        reactor.powerDraw = -60f;
        reactor.mass = 4f;
        loadout.getSlot("reactor_0").installedModule = reactor;

        eventBus.publish(new ModuleInstalledEvent(ship, "reactor_0", reactor, null));

        ShipDataComponent data = ship.getComponent(ShipDataComponent.class);
        assertEquals(8004f, data.mass, 0.01f);
    }

    @Test
    void engineModule_appliesStatMultipliers() {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        ShipDataComponent data = ship.getComponent(ShipDataComponent.class);

        float baseThrust = data.maxThrust;
        float baseTurnRate = data.maxTurnRate;
        float baseMaxSpeed = data.maxSpeed;

        ShipModuleData eng = new ShipModuleData();
        eng.id = "engine_mk1";
        eng.category = ShipModuleCategory.ENGINE;
        eng.powerDraw = 15f;
        eng.mass = 3f;
        eng.stats.put("thrustMultiplier", 1.3f);
        eng.stats.put("turnRateMultiplier", 1.1f);
        eng.stats.put("maxSpeedBonus", 15f);
        loadout.getSlot("engine_0").installedModule = eng;

        eventBus.publish(new ModuleInstalledEvent(ship, "engine_0", eng, null));

        assertEquals(baseThrust * 1.3f, data.maxThrust, 0.1f);
        assertEquals(baseTurnRate * 1.1f, data.maxTurnRate, 0.1f);
        assertEquals(baseMaxSpeed + 15f, data.maxSpeed, 0.1f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.OutfitterSystemTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Write OutfitterSystem**

```java
package com.galacticodyssey.ship.modules.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.modules.ShipModuleCategory;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.modules.events.ModuleInstalledEvent;
import com.galacticodyssey.ship.modules.events.ModuleUninstalledEvent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;

public class OutfitterSystem extends EntitySystem {

    private static final int PRIORITY = 3;
    private final EventBus eventBus;

    public OutfitterSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(ModuleInstalledEvent.class, e -> recalculate(e.shipEntity));
        eventBus.subscribe(ModuleUninstalledEvent.class, e -> recalculate(e.shipEntity));
    }

    @Override
    public void update(float deltaTime) {}

    public void recalculate(Entity shipEntity) {
        ShipDataComponent data = shipEntity.getComponent(ShipDataComponent.class);
        ShipLoadoutComponent loadout = shipEntity.getComponent(ShipLoadoutComponent.class);
        if (data == null || loadout == null) return;

        float baseMass = data.hullBaseMass > 0 ? data.hullBaseMass : data.mass;
        if (data.hullBaseMass <= 0) data.hullBaseMass = data.mass;

        float totalModuleMass = loadout.getTotalModuleMass();

        float weaponMass = 0f;
        ShipHardpointComponent hpc = shipEntity.getComponent(ShipHardpointComponent.class);
        if (hpc != null) {
            for (Hardpoint hp : hpc.hardpoints) {
                if (hp.mountedWeapon != null) {
                    weaponMass += hp.mountedWeapon.mass;
                }
            }
        }

        data.mass = baseMass + totalModuleMass + weaponMass;

        float baseThrust = data.baseMaxThrust > 0 ? data.baseMaxThrust : data.maxThrust;
        float baseTurnRate = data.baseMaxTurnRate > 0 ? data.baseMaxTurnRate : data.maxTurnRate;
        float baseMaxSpeed = data.baseMaxSpeed > 0 ? data.baseMaxSpeed : data.maxSpeed;
        if (data.baseMaxThrust <= 0) data.baseMaxThrust = data.maxThrust;
        if (data.baseMaxTurnRate <= 0) data.baseMaxTurnRate = data.maxTurnRate;
        if (data.baseMaxSpeed <= 0) data.baseMaxSpeed = data.maxSpeed;

        float thrustMul = 1f;
        float turnMul = 1f;
        float speedBonus = 0f;

        for (ShipModuleSlot slot : loadout.moduleSlots) {
            ShipModuleData mod = slot.installedModule;
            if (mod == null || mod.category != ShipModuleCategory.ENGINE) continue;
            thrustMul *= mod.stats.getOrDefault("thrustMultiplier", 1f);
            turnMul *= mod.stats.getOrDefault("turnRateMultiplier", 1f);
            speedBonus += mod.stats.getOrDefault("maxSpeedBonus", 0f);
        }

        data.maxThrust = baseThrust * thrustMul;
        data.maxTurnRate = baseTurnRate * turnMul;
        data.maxSpeed = baseMaxSpeed + speedBonus;
    }
}
```

**Note:** This requires adding `hullBaseMass`, `baseMaxThrust`, `baseMaxTurnRate`, and `baseMaxSpeed` fields to `ShipDataComponent`. Add them as:

```java
public float hullBaseMass;
public float baseMaxThrust;
public float baseMaxTurnRate;
public float baseMaxSpeed;
```

These store the unmodified hull values so recalculation can apply multipliers from a known base.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.OutfitterSystemTest" --info`
Expected: Both tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/systems/OutfitterSystem.java core/src/test/java/com/galacticodyssey/ship/modules/OutfitterSystemTest.java core/src/main/java/com/galacticodyssey/ship/components/ShipDataComponent.java
git commit -m "feat(outfitter): add OutfitterSystem for ship stat recalculation on loadout change"
```

---

## Task 9: Outfitter Validation Logic

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/ship/modules/OutfitterValidationTest.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/modules/OutfitterValidator.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.modules;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutfitterValidationTest {

    private Entity ship;
    private ShipLoadoutComponent loadout;
    private ShipCargoComponent cargo;

    @BeforeEach
    void setUp() {
        ship = new Entity();
        loadout = new ShipLoadoutComponent();
        loadout.maxMass = 30f;
        loadout.moduleSlots.add(new ShipModuleSlot("reactor_0", ModuleSlotType.REACTOR, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("engine_0", ModuleSlotType.ENGINE, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("internal_0", ModuleSlotType.INTERNAL, HardpointSize.SMALL, false));
        ship.add(loadout);

        cargo = new ShipCargoComponent();
        ship.add(cargo);

        ShipModuleData reactor = new ShipModuleData();
        reactor.id = "reactor_sm";
        reactor.category = ShipModuleCategory.REACTOR;
        reactor.powerDraw = -60f;
        reactor.mass = 4f;
        reactor.size = HardpointSize.SMALL;
        loadout.getSlot("reactor_0").installedModule = reactor;

        ShipModuleData engine = new ShipModuleData();
        engine.id = "engine_sm";
        engine.category = ShipModuleCategory.ENGINE;
        engine.powerDraw = 15f;
        engine.mass = 3f;
        engine.size = HardpointSize.SMALL;
        loadout.getSlot("engine_0").installedModule = engine;
    }

    @Test
    void canInstall_validModule_returnsOk() {
        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_sm";
        shield.category = ShipModuleCategory.SHIELD_GENERATOR;
        shield.size = HardpointSize.SMALL;
        shield.powerDraw = 10f;
        shield.mass = 2f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", shield);
        assertTrue(result.allowed);
    }

    @Test
    void canInstall_wrongCategory_rejected() {
        ShipModuleData reactor2 = new ShipModuleData();
        reactor2.id = "reactor2";
        reactor2.category = ShipModuleCategory.REACTOR;
        reactor2.size = HardpointSize.SMALL;
        reactor2.powerDraw = -80f;
        reactor2.mass = 5f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", reactor2);
        assertFalse(result.allowed);
    }

    @Test
    void canInstall_overPowerBudget_rejected() {
        ShipModuleData hog = new ShipModuleData();
        hog.id = "power_hog";
        hog.category = ShipModuleCategory.MINING_LASER;
        hog.size = HardpointSize.SMALL;
        hog.powerDraw = 50f;
        hog.mass = 1f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", hog);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("power"));
    }

    @Test
    void canInstall_overMassBudget_rejected() {
        ShipModuleData heavy = new ShipModuleData();
        heavy.id = "heavy_mod";
        heavy.category = ShipModuleCategory.ARMOR_PLATING;
        heavy.size = HardpointSize.SMALL;
        heavy.powerDraw = 0f;
        heavy.mass = 25f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", heavy);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("mass"));
    }

    @Test
    void canInstall_tooLargeForSlot_rejected() {
        ShipModuleData big = new ShipModuleData();
        big.id = "big_shield";
        big.category = ShipModuleCategory.SHIELD_GENERATOR;
        big.size = HardpointSize.LARGE;
        big.powerDraw = 10f;
        big.mass = 2f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", big);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("size"));
    }

    @Test
    void canUninstall_mandatorySlot_rejected() {
        OutfitterValidator.Result result = OutfitterValidator.canUninstallModule(ship, "reactor_0");
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("mandatory"));
    }

    @Test
    void canUninstall_optionalSlot_allowed() {
        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_sm";
        shield.category = ShipModuleCategory.SHIELD_GENERATOR;
        shield.size = HardpointSize.SMALL;
        shield.powerDraw = 10f;
        shield.mass = 2f;
        loadout.getSlot("internal_0").installedModule = shield;

        OutfitterValidator.Result result = OutfitterValidator.canUninstallModule(ship, "internal_0");
        assertTrue(result.allowed);
    }

    @Test
    void canInstall_reactorDowngrade_wouldExceedPower_rejected() {
        ShipModuleData weakReactor = new ShipModuleData();
        weakReactor.id = "weak_reactor";
        weakReactor.category = ShipModuleCategory.REACTOR;
        weakReactor.size = HardpointSize.SMALL;
        weakReactor.powerDraw = -10f;
        weakReactor.mass = 2f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "reactor_0", weakReactor);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("power"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.OutfitterValidationTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Write OutfitterValidator**

```java
package com.galacticodyssey.ship.modules;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;

public final class OutfitterValidator {

    private OutfitterValidator() {}

    public static class Result {
        public final boolean allowed;
        public final String reason;

        private Result(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static Result ok() { return new Result(true, ""); }
        public static Result deny(String reason) { return new Result(false, reason); }
    }

    public static Result canInstallModule(Entity ship, String slotId, ShipModuleData module) {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        if (loadout == null) return Result.deny("Ship has no loadout");

        ShipModuleSlot slot = loadout.getSlot(slotId);
        if (slot == null) return Result.deny("Slot not found: " + slotId);

        if (!slot.accepts(module)) {
            if (module.size.ordinal() > slot.size.ordinal()) {
                return Result.deny("Module too large for this slot (size mismatch)");
            }
            return Result.deny("Module category not compatible with this slot");
        }

        float currentDraw = loadout.getTotalPowerDraw();
        float currentGen = loadout.getTotalPowerGeneration();

        float newDraw = currentDraw;
        float newGen = currentGen;

        if (slot.installedModule != null) {
            if (slot.installedModule.powerDraw > 0) newDraw -= slot.installedModule.powerDraw;
            if (slot.installedModule.powerDraw < 0) newGen -= Math.abs(slot.installedModule.powerDraw);
        }

        if (module.powerDraw > 0) newDraw += module.powerDraw;
        if (module.powerDraw < 0) newGen += Math.abs(module.powerDraw);

        if (newDraw > newGen) {
            return Result.deny("Insufficient power budget (" + newDraw + " MW draw > " + newGen + " MW generation)");
        }

        float currentMass = loadout.getTotalModuleMass();
        float newMass = currentMass;
        if (slot.installedModule != null) newMass -= slot.installedModule.mass;
        newMass += module.mass;

        if (newMass > loadout.maxMass) {
            return Result.deny("Exceeds mass budget (" + newMass + "t > " + loadout.maxMass + "t)");
        }

        return Result.ok();
    }

    public static Result canUninstallModule(Entity ship, String slotId) {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        if (loadout == null) return Result.deny("Ship has no loadout");

        ShipModuleSlot slot = loadout.getSlot(slotId);
        if (slot == null) return Result.deny("Slot not found: " + slotId);

        if (slot.isEmpty()) return Result.deny("Slot is already empty");

        if (slot.mandatory) {
            return Result.deny("Cannot uninstall from mandatory slot without replacement");
        }

        return Result.ok();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :core:test --tests "com.galacticodyssey.ship.modules.OutfitterValidationTest" --info`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/modules/OutfitterValidator.java core/src/test/java/com/galacticodyssey/ship/modules/OutfitterValidationTest.java
git commit -m "feat(outfitter): add OutfitterValidator with power, mass, size, and mandatory checks"
```

---

## Task 10: ShipFactory Integration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java`

- [ ] **Step 1: Add ShipModuleRegistry field and setter to ShipFactory**

Add after the `reactorSpecRegistry` field:

```java
private ShipModuleRegistry moduleRegistry;
```

Add setter:

```java
public void setModuleRegistry(ShipModuleRegistry registry) {
    this.moduleRegistry = registry;
}
```

- [ ] **Step 2: Add loadout and cargo components to createShip**

After the `entity.add(powerState)` line (around line 183), add:

```java
// Module loadout
ShipLoadoutComponent loadoutComp = buildLoadoutComponent(sizeClass);
entity.add(loadoutComp);

// Ship cargo (starts empty)
entity.add(new ShipCargoComponent());
```

- [ ] **Step 3: Add buildLoadoutComponent method**

Add after the `buildPowerState` method:

```java
private ShipLoadoutComponent buildLoadoutComponent(ShipSizeClass sizeClass) {
    ShipLoadoutComponent comp = new ShipLoadoutComponent();

    if (moduleRegistry == null) return comp;

    String classId = getModuleSlotClassId(sizeClass);
    ShipModuleRegistry.SlotLayout layout = moduleRegistry.getSlotLayout(classId);
    if (layout == null) return comp;

    comp.maxMass = layout.maxMass;

    for (ShipModuleRegistry.SlotTemplate t : layout.slots) {
        ShipModuleSlot slot = new ShipModuleSlot(t.id, t.slotType, t.size, t.mandatory);
        slot.position.set(t.posX, t.posY);

        if (t.defaultModuleId != null) {
            slot.installedModule = moduleRegistry.createModuleInstance(t.defaultModuleId);
        }

        comp.moduleSlots.add(slot);
    }

    return comp;
}

private static String getModuleSlotClassId(ShipSizeClass sizeClass) {
    switch (sizeClass) {
        case SMALL:  return "corvette_scout";
        case MEDIUM: return "frigate_patrol";
        default:     return "corvette_scout";
    }
}
```

- [ ] **Step 4: Add required imports to ShipFactory**

```java
import com.galacticodyssey.ship.modules.*;
import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
```

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/ShipFactory.java
git commit -m "feat(outfitter): wire ShipLoadoutComponent and ShipCargoComponent into ShipFactory"
```

---

## Task 11: OutfitterBudgetBar (UI)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterBudgetBar.java`

- [ ] **Step 1: Create OutfitterBudgetBar**

```java
package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

public class OutfitterBudgetBar extends Table implements Disposable {

    private final Skin skin;
    private Label powerLabel;
    private Label massLabel;
    private Label creditsLabel;
    private ProgressBar powerBar;
    private ProgressBar massBar;
    private Texture barBgTexture;
    private Texture barFillTexture;

    public OutfitterBudgetBar(Skin skin) {
        this.skin = skin;
    }

    public void initialize() {
        Pixmap bg = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bg.setColor(new Color(0.12f, 0.16f, 0.23f, 1f));
        bg.fill();
        barBgTexture = new Texture(bg);
        bg.dispose();

        Pixmap fill = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        fill.setColor(new Color(0.96f, 0.62f, 0.04f, 1f));
        fill.fill();
        barFillTexture = new Texture(fill);
        fill.dispose();

        pad(8, 16, 8, 16);
        setBackground(new TextureRegionDrawable(new TextureRegion(barBgTexture)));

        powerLabel = new Label("POWER: 0 / 0 MW", skin, "body");
        powerLabel.setColor(new Color(0.96f, 0.62f, 0.04f, 1f));
        massLabel = new Label("MASS: 0 / 0 t", skin, "body");
        massLabel.setColor(Color.LIGHT_GRAY);
        creditsLabel = new Label("Credits: 0", skin, "body");
        creditsLabel.setColor(new Color(0.98f, 0.75f, 0.15f, 1f));

        add(powerLabel).expandX().left();
        add(massLabel).expandX().center();
        add(creditsLabel).right();
    }

    public void update(float powerDraw, float powerGen, float mass, float maxMass, int credits, boolean stationMode) {
        powerLabel.setText(String.format("POWER: %.0f / %.0f MW", powerDraw, powerGen));
        float powerRatio = powerGen > 0 ? powerDraw / powerGen : 0f;
        powerLabel.setColor(budgetColor(powerRatio));

        massLabel.setText(String.format("MASS: %.1f / %.0f t", mass, maxMass));
        float massRatio = maxMass > 0 ? mass / maxMass : 0f;
        massLabel.setColor(budgetColor(massRatio));

        creditsLabel.setVisible(stationMode);
        if (stationMode) {
            creditsLabel.setText("Credits: " + credits);
        }
    }

    private Color budgetColor(float ratio) {
        if (ratio > 0.85f) return Color.RED;
        if (ratio > 0.6f)  return Color.YELLOW;
        return Color.WHITE;
    }

    @Override
    public void dispose() {
        if (barBgTexture != null) barBgTexture.dispose();
        if (barFillTexture != null) barFillTexture.dispose();
    }
}
```

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterBudgetBar.java
git commit -m "feat(outfitter): add OutfitterBudgetBar with power/mass gauges and credits"
```

---

## Task 12: OutfitterDetailPanel (UI)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterDetailPanel.java`

- [ ] **Step 1: Create OutfitterDetailPanel**

```java
package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ui.actors.ItemDetailPanel;

import java.util.Map;

public class OutfitterDetailPanel extends Table {

    private final Skin skin;
    private Label slotHeaderLabel;
    private Table currentSection;
    private Table candidateSection;
    private Table actionButtons;
    private TextButton installButton;
    private TextButton buyInstallButton;
    private TextButton uninstallButton;
    private TextButton sellButton;

    private Runnable onInstall;
    private Runnable onBuyInstall;
    private Runnable onUninstall;
    private Runnable onSell;

    public OutfitterDetailPanel(Skin skin) {
        this.skin = skin;
    }

    public void initialize() {
        pad(10);
        defaults().left().fillX();

        slotHeaderLabel = new Label("Select a slot", skin, "header");
        add(slotHeaderLabel).padBottom(8).row();

        currentSection = new Table();
        add(currentSection).padBottom(8).row();

        candidateSection = new Table();
        add(candidateSection).padBottom(12).row();

        actionButtons = new Table();
        installButton = new TextButton("Install", skin);
        buyInstallButton = new TextButton("Buy & Install", skin);
        uninstallButton = new TextButton("Uninstall", skin);
        sellButton = new TextButton("Sell", skin);

        installButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                if (onInstall != null) onInstall.run();
            }
        });
        buyInstallButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                if (onBuyInstall != null) onBuyInstall.run();
            }
        });
        uninstallButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                if (onUninstall != null) onUninstall.run();
            }
        });
        sellButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                if (onSell != null) onSell.run();
            }
        });

        actionButtons.defaults().fillX().padBottom(4);
        actionButtons.add(installButton).row();
        actionButtons.add(buyInstallButton).row();
        actionButtons.add(uninstallButton).row();
        actionButtons.add(sellButton).row();
        add(actionButtons).expandY().bottom();
    }

    public void setCallbacks(Runnable onInstall, Runnable onBuyInstall, Runnable onUninstall, Runnable onSell) {
        this.onInstall = onInstall;
        this.onBuyInstall = onBuyInstall;
        this.onUninstall = onUninstall;
        this.onSell = onSell;
    }

    public void showSlot(ShipModuleSlot slot, boolean stationMode) {
        slotHeaderLabel.setText(slot.id + " — " + slot.slotType + " (" + slot.size + ")");
        showCurrentModule(slot.installedModule);
        candidateSection.clear();
        updateButtons(slot, null, stationMode, false);
    }

    public void showCurrentModule(ShipModuleData module) {
        currentSection.clear();
        currentSection.defaults().left();
        Label header = new Label("CURRENTLY INSTALLED", skin, "body");
        header.setColor(Color.GRAY);
        currentSection.add(header).padBottom(4).row();

        if (module == null) {
            currentSection.add(new Label("Empty", skin, "body")).row();
        } else {
            Label nameLabel = new Label(module.name, skin, "body");
            nameLabel.setColor(ItemDetailPanel.getQualityColor(module.qualityTier));
            currentSection.add(nameLabel).padBottom(2).row();
            addStatLines(currentSection, module, null);
        }
    }

    public void showCandidate(ShipModuleData candidate, ShipModuleData current) {
        candidateSection.clear();
        if (candidate == null) return;
        candidateSection.defaults().left();

        Label header = new Label("COMPARING TO", skin, "body");
        header.setColor(Color.GRAY);
        candidateSection.add(header).padBottom(4).row();

        Label nameLabel = new Label(candidate.name, skin, "body");
        nameLabel.setColor(ItemDetailPanel.getQualityColor(candidate.qualityTier));
        candidateSection.add(nameLabel).padBottom(2).row();
        addStatLines(candidateSection, candidate, current);
    }

    public void updateButtons(ShipModuleSlot slot, ShipModuleData candidate,
                              boolean stationMode, boolean fromStation) {
        installButton.setVisible(candidate != null && !fromStation);
        buyInstallButton.setVisible(candidate != null && fromStation && stationMode);
        uninstallButton.setVisible(slot != null && !slot.isEmpty());
        sellButton.setVisible(stationMode && slot != null && !slot.isEmpty() && !slot.mandatory);
    }

    public void setInstallEnabled(boolean enabled) {
        installButton.setDisabled(!enabled);
        buyInstallButton.setDisabled(!enabled);
    }

    private void addStatLines(Table table, ShipModuleData module, ShipModuleData compareWith) {
        addStatRow(table, "Power", module.powerDraw, compareWith != null ? compareWith.powerDraw : Float.NaN, "MW", true);
        addStatRow(table, "Mass", module.mass, compareWith != null ? compareWith.mass : Float.NaN, "t", true);
        for (Map.Entry<String, Float> entry : module.stats.entrySet()) {
            float compareVal = compareWith != null ? compareWith.stats.getOrDefault(entry.getKey(), 0f) : Float.NaN;
            addStatRow(table, entry.getKey(), entry.getValue(), compareVal, "", false);
        }
    }

    private void addStatRow(Table table, String label, float value, float compareValue, String unit, boolean lowerIsBetter) {
        String text = label + ": " + String.format("%.1f", value) + unit;
        if (!Float.isNaN(compareValue) && Math.abs(value - compareValue) > 0.01f) {
            float diff = value - compareValue;
            boolean better = lowerIsBetter ? diff < 0 : diff > 0;
            String arrow = better ? " ▲" : " ▼";
            text += arrow;
            Label l = new Label(text, skin, "body");
            l.setColor(better ? Color.GREEN : Color.RED);
            table.add(l).padBottom(1).row();
        } else {
            table.add(new Label(text, skin, "body")).padBottom(1).row();
        }
    }

    public void clearAll() {
        slotHeaderLabel.setText("Select a slot");
        currentSection.clear();
        candidateSection.clear();
        installButton.setVisible(false);
        buyInstallButton.setVisible(false);
        uninstallButton.setVisible(false);
        sellButton.setVisible(false);
    }
}
```

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterDetailPanel.java
git commit -m "feat(outfitter): add OutfitterDetailPanel with stat comparison and action buttons"
```

---

## Task 13: OutfitterInventoryPanel (UI)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterInventoryPanel.java`

- [ ] **Step 1: Create OutfitterInventoryPanel**

```java
package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.modules.ShipModuleRegistry;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ui.actors.ItemDetailPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OutfitterInventoryPanel extends Table {

    private final Skin skin;
    private TextButton cargoTab;
    private TextButton stationTab;
    private TextField filterField;
    private Table itemListTable;
    private ScrollPane scrollPane;
    private boolean showingCargo = true;

    private Consumer<ShipModuleData> onModuleSelected;
    private Consumer<ShipWeaponData> onWeaponSelected;
    private Consumer<ShipModuleData> onModuleDoubleClicked;
    private boolean stationMode;

    public OutfitterInventoryPanel(Skin skin) {
        this.skin = skin;
    }

    public void initialize(boolean stationMode) {
        this.stationMode = stationMode;
        pad(8);
        defaults().fillX();

        Table tabBar = new Table();
        cargoTab = new TextButton("Cargo", skin);
        stationTab = new TextButton("Station", skin);
        cargoTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { showCargo(); }
        });
        stationTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { showStation(); }
        });
        tabBar.add(cargoTab).expandX().fillX();
        if (stationMode) tabBar.add(stationTab).expandX().fillX();
        add(tabBar).padBottom(6).row();

        filterField = new TextField("", skin);
        filterField.setMessageText("Filter...");
        filterField.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                // Refresh handled externally via refreshModules
            }
        });
        add(filterField).padBottom(6).row();

        itemListTable = new Table();
        itemListTable.top().defaults().fillX().padBottom(3);
        scrollPane = new ScrollPane(itemListTable, skin);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).expand().fill();
    }

    public void setOnModuleSelected(Consumer<ShipModuleData> callback) { this.onModuleSelected = callback; }
    public void setOnWeaponSelected(Consumer<ShipWeaponData> callback) { this.onWeaponSelected = callback; }
    public void setOnModuleDoubleClicked(Consumer<ShipModuleData> callback) { this.onModuleDoubleClicked = callback; }

    public void refreshModules(List<ShipModuleData> modules, ShipModuleSlot selectedSlot) {
        itemListTable.clear();
        String filter = filterField.getText().toLowerCase();

        for (ShipModuleData mod : modules) {
            if (!filter.isEmpty() && !mod.name.toLowerCase().contains(filter)) continue;
            if (selectedSlot != null && !selectedSlot.accepts(mod)) continue;

            Table row = new Table(skin);
            row.pad(4, 6, 4, 6);
            Label nameLabel = new Label(mod.name, skin, "body");
            nameLabel.setColor(ItemDetailPanel.getQualityColor(mod.qualityTier));
            Label infoLabel = new Label(mod.category.name() + " | " + mod.size, skin, "body");
            infoLabel.setColor(Color.GRAY);
            infoLabel.setFontScale(0.8f);

            row.add(nameLabel).left().row();
            row.add(infoLabel).left();

            row.addListener(new ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) {
                    if (getTapCount() >= 2 && onModuleDoubleClicked != null) {
                        onModuleDoubleClicked.accept(mod);
                    } else if (onModuleSelected != null) {
                        onModuleSelected.accept(mod);
                    }
                }
            });

            itemListTable.add(row).row();
        }
    }

    public void refreshWeapons(List<ShipWeaponData> weapons, HardpointSize maxSize) {
        itemListTable.clear();
        String filter = filterField.getText().toLowerCase();

        for (ShipWeaponData wep : weapons) {
            if (!filter.isEmpty() && !wep.name.toLowerCase().contains(filter)) continue;

            Table row = new Table(skin);
            row.pad(4, 6, 4, 6);
            Label nameLabel = new Label(wep.name, skin, "body");
            Label infoLabel = new Label(wep.category + " | " + String.format("%.0f dmg", wep.damage), skin, "body");
            infoLabel.setColor(Color.GRAY);
            infoLabel.setFontScale(0.8f);

            row.add(nameLabel).left().row();
            row.add(infoLabel).left();

            row.addListener(new ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) {
                    if (onWeaponSelected != null) onWeaponSelected.accept(wep);
                }
            });

            itemListTable.add(row).row();
        }
    }

    public boolean isShowingCargo() { return showingCargo; }
    public String getFilterText() { return filterField.getText(); }
    public void toggleTab() { if (showingCargo) showStation(); else showCargo(); }

    private void showCargo() {
        showingCargo = true;
        cargoTab.setColor(Color.WHITE);
        stationTab.setColor(Color.GRAY);
    }

    private void showStation() {
        if (!stationMode) return;
        showingCargo = false;
        cargoTab.setColor(Color.GRAY);
        stationTab.setColor(Color.WHITE);
    }
}
```

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterInventoryPanel.java
git commit -m "feat(outfitter): add OutfitterInventoryPanel with cargo/station tabs and filtering"
```

---

## Task 14: ShipSilhouetteActor (UI)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/outfitter/ShipSilhouetteActor.java`

- [ ] **Step 1: Create ShipSilhouetteActor**

```java
package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.galacticodyssey.ship.modules.ModuleSlotType;
import com.galacticodyssey.ship.modules.ShipModuleRegistry;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;

import java.util.function.Consumer;

public class ShipSilhouetteActor extends Widget {

    private static final Color HULL_FILL = new Color(0.07f, 0.09f, 0.14f, 0.8f);
    private static final Color HULL_STROKE = new Color(0.23f, 0.51f, 0.96f, 1f);
    private static final Color WEAPON_SLOT_COLOR = new Color(0.96f, 0.62f, 0.04f, 0.9f);
    private static final Color CORE_MODULE_COLOR = new Color(0.13f, 0.83f, 0.93f, 1f);
    private static final Color OPTIONAL_SLOT_COLOR = new Color(0.28f, 0.33f, 0.41f, 1f);
    private static final Color ENGINE_GLOW = new Color(0.98f, 0.45f, 0.09f, 0.6f);
    private static final Color SELECTED_COLOR = new Color(0.4f, 0.8f, 1f, 1f);

    private ShapeRenderer shapeRenderer;
    private ShipModuleRegistry.SlotLayout slotLayout;
    private ShipLoadoutComponent loadout;
    private ShipHardpointComponent hardpoints;
    private String selectedSlotId;
    private boolean showWeaponSlots = true;
    private boolean showModuleSlots = true;

    private Consumer<String> onSlotClicked;
    private float pulseTimer;

    public ShipSilhouetteActor() {
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleClick(x, y);
            }
        });
    }

    public void initialize() {
        shapeRenderer = new ShapeRenderer();
    }

    public void setData(ShipModuleRegistry.SlotLayout layout,
                        ShipLoadoutComponent loadout,
                        ShipHardpointComponent hardpoints) {
        this.slotLayout = layout;
        this.loadout = loadout;
        this.hardpoints = hardpoints;
    }

    public void setOnSlotClicked(Consumer<String> callback) { this.onSlotClicked = callback; }
    public void setSelectedSlotId(String id) { this.selectedSlotId = id; }
    public void setShowWeaponSlots(boolean show) { this.showWeaponSlots = show; }
    public void setShowModuleSlots(boolean show) { this.showModuleSlots = show; }

    @Override
    public void act(float delta) {
        super.act(delta);
        pulseTimer += delta * 3f;
    }

    @Override
    public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
        if (slotLayout == null) return;

        batch.end();
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.setTransformMatrix(batch.getTransformMatrix());

        float ox = getX();
        float oy = getY();
        float w = getWidth();
        float h = getHeight();
        float scale = Math.min(w / 200f, h / 360f);
        float cx = ox + w / 2f;
        float cy = oy + h / 2f;

        drawHull(cx, cy, scale);
        if (showModuleSlots) drawModuleSlots(cx, cy, scale);
        if (showWeaponSlots) drawHardpointSlots(cx, cy, scale);

        batch.begin();
    }

    private void drawHull(float cx, float cy, float scale) {
        if (slotLayout.silhouettePoints == null) return;
        float[] pts = slotLayout.silhouettePoints;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(HULL_FILL);
        for (int i = 0; i < pts.length - 2; i += 2) {
            float x0 = cx + (pts[0] - 100) * scale;
            float y0 = cy + (170 - pts[1]) * scale;
            float x1 = cx + (pts[i] - 100) * scale;
            float y1 = cy + (170 - pts[i + 1]) * scale;
            float x2 = cx + (pts[i + 2] - 100) * scale;
            float y2 = cy + (170 - pts[i + 3]) * scale;
            shapeRenderer.triangle(x0, y0, x1, y1, x2, y2);
        }
        shapeRenderer.end();

        if (slotLayout.wingPolygons != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(HULL_FILL);
            for (float[] wing : slotLayout.wingPolygons) {
                for (int i = 0; i < wing.length - 2; i += 2) {
                    float x0 = cx + (wing[0] - 100) * scale;
                    float y0 = cy + (170 - wing[1]) * scale;
                    float x1 = cx + (wing[i] - 100) * scale;
                    float y1 = cy + (170 - wing[i + 1]) * scale;
                    float x2 = cx + (wing[i + 2] - 100) * scale;
                    float y2 = cy + (170 - wing[i + 3]) * scale;
                    shapeRenderer.triangle(x0, y0, x1, y1, x2, y2);
                }
            }
            shapeRenderer.end();
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(HULL_STROKE);
        for (int i = 0; i < pts.length - 2; i += 2) {
            float x1 = cx + (pts[i] - 100) * scale;
            float y1 = cy + (170 - pts[i + 1]) * scale;
            float x2 = cx + (pts[i + 2] - 100) * scale;
            float y2 = cy + (170 - pts[i + 3]) * scale;
            shapeRenderer.line(x1, y1, x2, y2);
        }
        float lx = cx + (pts[pts.length - 2] - 100) * scale;
        float ly = cy + (170 - pts[pts.length - 1]) * scale;
        float fx = cx + (pts[0] - 100) * scale;
        float fy = cy + (170 - pts[1]) * scale;
        shapeRenderer.line(lx, ly, fx, fy);
        shapeRenderer.end();

        if (slotLayout.engineGlows != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(ENGINE_GLOW);
            for (int i = 0; i < slotLayout.engineGlows.length; i += 2) {
                float ex = cx + (slotLayout.engineGlows[i] - 100) * scale;
                float ey = cy + (170 - slotLayout.engineGlows[i + 1]) * scale;
                shapeRenderer.ellipse(ex - 6 * scale, ey - 3 * scale, 12 * scale, 6 * scale);
            }
            shapeRenderer.end();
        }
    }

    private void drawModuleSlots(float cx, float cy, float scale) {
        if (loadout == null) return;
        for (ShipModuleSlot slot : loadout.moduleSlots) {
            float sx = cx + (slot.position.x - 100) * scale;
            float sy = cy + (170 - slot.position.y) * scale;
            float slotW = 30 * scale;
            float slotH = 16 * scale;

            boolean selected = slot.id.equals(selectedSlotId);
            Color color = slot.mandatory ? CORE_MODULE_COLOR : OPTIONAL_SLOT_COLOR;
            if (selected) {
                float pulse = 0.7f + 0.3f * (float) Math.sin(pulseTimer);
                color = new Color(SELECTED_COLOR.r, SELECTED_COLOR.g, SELECTED_COLOR.b, pulse);
            }

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(new Color(0.07f, 0.09f, 0.14f, 0.9f));
            shapeRenderer.rect(sx - slotW / 2, sy - slotH / 2, slotW, slotH);
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(color);
            shapeRenderer.rect(sx - slotW / 2, sy - slotH / 2, slotW, slotH);
            shapeRenderer.end();
        }
    }

    private void drawHardpointSlots(float cx, float cy, float scale) {
        if (hardpoints == null) return;
        for (Hardpoint hp : hardpoints.hardpoints) {
            float hx = cx + hp.position.x * scale;
            float hy = cy + (170 - hp.position.z * 50) * scale;
            float radius = 8 * scale;

            boolean selected = hp.id.equals(selectedSlotId);
            Color color = selected
                ? new Color(SELECTED_COLOR.r, SELECTED_COLOR.g, SELECTED_COLOR.b,
                            0.7f + 0.3f * (float) Math.sin(pulseTimer))
                : WEAPON_SLOT_COLOR;

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(new Color(0.07f, 0.09f, 0.14f, 0.9f));
            shapeRenderer.circle(hx, hy, radius);
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(color);
            shapeRenderer.circle(hx, hy, radius);
            shapeRenderer.end();
        }
    }

    private void handleClick(float x, float y) {
        if (slotLayout == null) return;
        float w = getWidth();
        float h = getHeight();
        float scale = Math.min(w / 200f, h / 360f);
        float cx = w / 2f;
        float cy = h / 2f;

        if (showModuleSlots && loadout != null) {
            for (ShipModuleSlot slot : loadout.moduleSlots) {
                float sx = cx + (slot.position.x - 100) * scale;
                float sy = cy + (170 - slot.position.y) * scale;
                if (Math.abs(x - sx) < 20 * scale && Math.abs(y - sy) < 12 * scale) {
                    if (onSlotClicked != null) onSlotClicked.accept(slot.id);
                    return;
                }
            }
        }

        if (showWeaponSlots && hardpoints != null) {
            for (Hardpoint hp : hardpoints.hardpoints) {
                float hx = cx + hp.position.x * scale;
                float hy = cy + (170 - hp.position.z * 50) * scale;
                float dist = Vector2.dst(x, y, hx, hy);
                if (dist < 12 * scale) {
                    if (onSlotClicked != null) onSlotClicked.accept(hp.id);
                    return;
                }
            }
        }
    }

    public void dispose() {
        if (shapeRenderer != null) shapeRenderer.dispose();
    }
}
```

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/outfitter/ShipSilhouetteActor.java
git commit -m "feat(outfitter): add ShipSilhouetteActor with hull rendering and clickable slots"
```

---

## Task 15: OutfitterScreenSystem (Main Controller)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterScreenSystem.java`

- [ ] **Step 1: Create OutfitterScreenSystem**

This is the main controller, following the `InventoryScreenSystem` pattern exactly. Read `core/src/main/java/com/galacticodyssey/ui/systems/InventoryScreenSystem.java` for the full pattern reference. The OutfitterScreenSystem should:

- Constructor takes `EventBus`, `Skin`, and `ShipModuleRegistry`
- `initialize(Engine)` builds the Stage, creates all child panels, wires callbacks
- `open(Entity shipEntity, boolean stationMode)` / `close()` / `toggle(Entity, boolean)` manage state
- `getStage()` returns the Stage for input routing
- `render(float delta)` acts and draws the stage
- `resize(int, int)` updates viewport
- Implements `Disposable`
- Layout: root Table with top bar (tabs + ship name), then 3-column row (inventory left, silhouette center, detail right), then budget bar bottom
- Category tabs (WEAPONS / MODULES / COSMETICS) filter which slots the silhouette shows and what the inventory panel lists
- Selection flow: slot click → filter inventory → item click → show comparison → install/uninstall buttons
- On install: validate via `OutfitterValidator`, move items between cargo/slots, publish events, refresh UI
- On open: publish `OutfitterOpenedEvent`; on close: publish `OutfitterClosedEvent`

This is the largest single file. The full implementation should follow the patterns from `InventoryScreenSystem` (Table layout, DragAndDrop setup, event wiring, player entity lookup) combined with the outfitter-specific logic (category tabs, slot selection, module filtering, budget updates, validation).

Write the complete file with all the wiring for:
- Tab switching (weapons shows hardpoints + weapon inventory, modules shows module slots + module inventory, cosmetics shows stub)
- Slot selection callbacks from ShipSilhouetteActor
- Item selection callbacks from OutfitterInventoryPanel
- Install/uninstall/buy/sell button callbacks from OutfitterDetailPanel
- Budget bar updates from ShipLoadoutComponent state
- Keyboard shortcuts (ESC close, 1/2/3 tabs, Tab cargo/station toggle)

- [ ] **Step 2: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/outfitter/OutfitterScreenSystem.java
git commit -m "feat(outfitter): add OutfitterScreenSystem main controller with full wiring"
```

---

## Task 16: GameScreen Integration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add outfitter field and build method**

Add field near the inventory fields (around line 149):

```java
private OutfitterScreenSystem outfitterScreenSystem;
private boolean inOutfitter;
```

Add build method (after `buildInventorySystem`):

```java
private void buildOutfitterSystem() {
    EventBus eventBus = gameWorld.getEventBus();
    ShipModuleRegistry moduleRegistry = new ShipModuleRegistry();
    moduleRegistry.loadModules("data/modules/ship_modules.json");
    moduleRegistry.loadSlotLayouts("data/modules/ship_module_slots.json");

    outfitterScreenSystem = new OutfitterScreenSystem(eventBus, game.getSkin(), moduleRegistry);
    outfitterScreenSystem.initialize(gameWorld.getEngine());

    shipFactory.setModuleRegistry(moduleRegistry);

    eventBus.subscribe(OutfitterOpenedEvent.class, event -> {
        inOutfitter = true;
        Gdx.input.setCursorCatched(false);
        gameWorld.getPlayerInputSystem().setEnabled(false);
        inputMultiplexer.clear();
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    outfitterScreenSystem.close();
                    return true;
                }
                return false;
            }
        });
        inputMultiplexer.addProcessor(outfitterScreenSystem.getStage());
    });

    eventBus.subscribe(OutfitterClosedEvent.class, event -> {
        inOutfitter = false;
        Gdx.input.setCursorCatched(true);
        gameWorld.getPlayerInputSystem().setEnabled(true);
        setupInput();
    });
}
```

- [ ] **Step 2: Call buildOutfitterSystem in initializeWorld**

After the `buildInventorySystem()` call (around line 274), add:

```java
buildOutfitterSystem();
```

- [ ] **Step 3: Add outfitter rendering**

In the `render` method, after the inventory render block (around line 1073), add:

```java
if (outfitterScreenSystem != null) {
    outfitterScreenSystem.render(delta);
}
```

- [ ] **Step 4: Add outfitter resize**

In `resize`, after the inventory resize line, add:

```java
if (outfitterScreenSystem != null) outfitterScreenSystem.resize(width, height);
```

- [ ] **Step 5: Add outfitter dispose**

In `dispose`, after the inventory dispose block, add:

```java
if (outfitterScreenSystem != null) {
    outfitterScreenSystem.dispose();
    outfitterScreenSystem = null;
}
```

- [ ] **Step 6: Add temporary test keybind**

In the `setupInput` escape handler, add after the TAB/inventory block:

```java
if (keycode == Input.Keys.O && !paused && !inDialog && !inInventory && !inOutfitter) {
    Entity playerShip = shipEntities.size > 0 ? shipEntities.first() : null;
    if (playerShip != null) {
        outfitterScreenSystem.open(playerShip, true);
    }
    return true;
}
```

- [ ] **Step 7: Add required imports**

```java
import com.galacticodyssey.ui.outfitter.OutfitterScreenSystem;
import com.galacticodyssey.ui.events.OutfitterOpenedEvent;
import com.galacticodyssey.ui.events.OutfitterClosedEvent;
import com.galacticodyssey.ship.modules.ShipModuleRegistry;
```

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(outfitter): wire OutfitterScreenSystem into GameScreen with O keybind"
```

---

## Task 17: Persistence Registration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java`

- [ ] **Step 1: Add snapshot classes**

Create minimal snapshot classes:

`core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipLoadoutSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

public class ShipLoadoutSnapshot {
    public float maxMass;
    public Map<String, String> installedModules = new HashMap<>();
}
```

`core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipCargoSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class ShipCargoSnapshot {
    public List<String> weaponIds = new ArrayList<>();
    public List<String> moduleIds = new ArrayList<>();
}
```

- [ ] **Step 2: Register in SnapshotComponentRegistry**

Add after the `PowerState` registration in the static block:

```java
register("ShipLoadout", ShipLoadoutSnapshot.class, ShipLoadoutComponent::new);
register("ShipCargo",   ShipCargoSnapshot.class,   ShipCargoComponent::new);
```

Add the required imports:

```java
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.persistence.snapshots.ShipLoadoutSnapshot;
import com.galacticodyssey.persistence.snapshots.ShipCargoSnapshot;
```

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipLoadoutSnapshot.java core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipCargoSnapshot.java core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java
git commit -m "feat(outfitter): register ShipLoadout and ShipCargo components for persistence"
```

---

## Task 18: Visual Verification

- [ ] **Step 1: Build the project**

Run: `gradlew :core:test --info`
Expected: All tests pass, including the new outfitter tests.

- [ ] **Step 2: Run the game**

Run: `gradlew :desktop:run`
Expected: Game launches normally.

- [ ] **Step 3: Open the outfitter**

Press `O` to open the outfitter screen.
Expected:
- Full-screen overlay appears with dark background
- Ship silhouette visible in center with hull outline, hardpoint circles, and module slot rectangles
- Left panel shows Cargo/Station tabs
- Right panel shows "Select a slot"
- Bottom bar shows power and mass gauges

- [ ] **Step 4: Test slot selection**

Click on a module slot (reactor/engine/internal) in the silhouette.
Expected:
- Slot highlights with pulsing border
- Right panel updates to show slot info and currently installed module (if any)
- Left panel filters to compatible items

- [ ] **Step 5: Test tab switching**

Press `1`, `2`, `3` to switch between Weapons/Modules/Cosmetics tabs.
Expected:
- Weapons tab shows hardpoint circles, hides module slots
- Modules tab shows module slots, hides hardpoints
- Cosmetics tab shows "coming soon" placeholder

- [ ] **Step 6: Close the outfitter**

Press `ESC` to close.
Expected: Overlay disappears, cursor re-captured, normal gameplay input restored.

- [ ] **Step 7: Commit verification notes**

```
git commit --allow-empty -m "verify(outfitter): visual verification complete — screen layout, slot selection, tab switching, close all working"
```
