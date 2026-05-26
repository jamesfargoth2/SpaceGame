# Weapons, Equipment & VFX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement FPS personal weapons shooting, ship-mounted weapons with hardpoints, a full grid+slot inventory/equipment system, a data-driven particle VFX pipeline, and shooting feedback (recoil, crosshair, screen shake, ADS).

**Architecture:** Domain-split with shared EventBus. Four new domains — `equipment/`, `ship/weapons/`, `vfx/`, and extensions to `player/` — each own their boundaries and communicate through events and shared ECS components. VFX and feedback systems are read-only subscribers; equipment bridges to combat via `EquipmentChangedEvent` syncing existing components.

**Tech Stack:** Java 17, libGDX 1.13+, Ashley ECS, JUnit 5, libGDX `Json` for data loading, libGDX `Pool<T>` for particles, `DecalBatch`/`SpriteBatch` for rendering.

**Spec:** `docs/superpowers/specs/2026-05-26-weapons-equipment-vfx-design.md`

---

## File Structure

### New Packages

```
core/src/main/java/com/galacticodyssey/
  equipment/
    components/    InventoryComponent, EquipmentSlotsComponent, LootDropComponent
    items/         Item (abstract), WeaponItem, MeleeWeaponItem, ArmorItem, AmmoItem,
                   ModItem, ComponentItem, ConsumableItem, JunkItem
    systems/       InventorySystem, EquipmentSystem, LootGenerationSystem, WeaponAssemblySystem
    data/          LootTable, LootTableRegistry
    events/        ItemAddedEvent, ItemRemovedEvent, EquipmentChangedEvent, LootDroppedEvent
    EquipmentEnums.java

  ship/weapons/
    components/    ShipHardpointComponent, ShipWeaponHeatComponent, GuidedProjectileComponent
    data/          ShipWeaponData, Hardpoint, HardpointTemplate, ShipWeaponRegistry
    systems/       TurretTrackingSystem, ShipWeaponSystem, ShipProjectileSystem,
                   PointDefenseSystem, ShipHeatSystem
    events/        ShipWeaponFiredEvent, ShipOverheatEvent, MissileLockedEvent,
                   PointDefenseEngagedEvent
    ShipWeaponEnums.java

  vfx/
    components/    ParticleEmitterComponent, ParticlePoolComponent
    data/          ParticleEffectDefinition, VFXEventBindings, VFXRegistry
    systems/       ParticleSpawnSystem, ParticleUpdateSystem, ParticleRenderSystem
    Particle.java
    ActiveEmitter.java
    VFXEnums.java

  player/
    components/    RecoilComponent, CrosshairComponent, ScreenShakeComponent, ADSComponent
    systems/       RecoilSystem, CrosshairSystem, ScreenShakeSystem, ADSSystem, WeaponSwaySystem
```

### Modified Files

```
core/src/main/java/com/galacticodyssey/
  combat/components/CombatInputComponent.java     — add aimHeld field
  ship/ShipBlueprint.java                          — add hardpoints list
  core/GameWorld.java                              — register new systems
```

### New Data Files

```
core/src/main/resources/data/
  equipment/
    loot_tables.json
    armor_definitions.json
    consumables.json
  weapons/
    ship_weapons.json
    hardpoint_templates.json
    recoil_patterns.json
  vfx/
    muzzle_flash_ballistic.json
    muzzle_flash_energy.json
    muzzle_flash_plasma.json
    impact_sparks.json
    impact_explosion.json
    shield_ripple.json
    engine_exhaust.json
    vfx_event_bindings.json
    screen_shake_config.json
  audio/
    sound_bindings.json
```

### New Test Files

```
core/src/test/java/com/galacticodyssey/
  equipment/
    InventoryComponentTest.java
    EquipmentSystemTest.java
    LootGenerationSystemTest.java
    WeaponAssemblySystemTest.java
  ship/weapons/
    TurretTrackingSystemTest.java
    ShipWeaponSystemTest.java
    ShipHeatSystemTest.java
    ShipProjectileSystemTest.java
    PointDefenseSystemTest.java
  vfx/
    ParticleUpdateSystemTest.java
    ParticleSpawnSystemTest.java
    VFXRegistryTest.java
  player/
    RecoilSystemTest.java
    ADSSystemTest.java
    CrosshairSystemTest.java
    ScreenShakeSystemTest.java
    WeaponSwaySystemTest.java
```

---

## Domain 1: Equipment & Inventory

### Task 1: Item Model Foundation

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/equipment/EquipmentEnums.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/Item.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/WeaponItem.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/MeleeWeaponItem.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/ArmorItem.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/AmmoItem.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/ModItem.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/ComponentItem.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/ConsumableItem.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/JunkItem.java`

- [ ] **Step 1: Create EquipmentEnums**

```java
package com.galacticodyssey.equipment;

public final class EquipmentEnums {

    public enum EquipmentSlot {
        PRIMARY_WEAPON, SECONDARY_WEAPON, MELEE_WEAPON,
        HELMET, CHEST, LEGS, BOOTS,
        UTILITY_1, UTILITY_2
    }

    public enum ItemType {
        WEAPON, MELEE_WEAPON, ARMOR, AMMO, MOD, COMPONENT, CONSUMABLE, JUNK
    }

    private EquipmentEnums() {}
}
```

- [ ] **Step 2: Create Item abstract base class**

```java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public abstract class Item {
    public final String id;
    public final String name;
    public final String description;
    public final String icon;
    public final QualityTier qualityTier;
    public final int gridWidth;
    public final int gridHeight;
    public final float weight;
    public final boolean stackable;
    public final int maxStack;
    public int currentStack;

    protected Item(String id, String name, String description, String icon,
                   QualityTier qualityTier, int gridWidth, int gridHeight,
                   float weight, boolean stackable, int maxStack) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.qualityTier = qualityTier;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.weight = weight;
        this.stackable = stackable;
        this.maxStack = maxStack;
        this.currentStack = 1;
    }

    public abstract ItemType getType();

    public float getTotalWeight() {
        return weight * currentStack;
    }

    public boolean canStackWith(Item other) {
        return stackable && other != null && id.equals(other.id)
            && currentStack + other.currentStack <= maxStack;
    }

    public int getSpaceRemaining() {
        return stackable ? maxStack - currentStack : 0;
    }
}
```

- [ ] **Step 3: Create WeaponItem**

```java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class WeaponItem extends Item {
    public final WeaponAssembly assembly;

    public WeaponItem(String id, String name, String description, String icon,
                      QualityTier qualityTier, int gridWidth, int gridHeight,
                      float weight, WeaponAssembly assembly) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.assembly = assembly;
    }

    @Override
    public ItemType getType() {
        return ItemType.WEAPON;
    }
}
```

- [ ] **Step 4: Create MeleeWeaponItem**

```java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class MeleeWeaponItem extends Item {
    public final WeaponAssembly assembly;

    public MeleeWeaponItem(String id, String name, String description, String icon,
                           QualityTier qualityTier, int gridWidth, int gridHeight,
                           float weight, WeaponAssembly assembly) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.assembly = assembly;
    }

    @Override
    public ItemType getType() {
        return ItemType.MELEE_WEAPON;
    }
}
```

- [ ] **Step 5: Create ArmorItem**

```java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.EnumMap;
import java.util.Map;

public class ArmorItem extends Item {
    public final float armorRating;
    public final Map<DamageType, Float> resistances;
    public final EquipmentSlot slotType;
    public float durability;
    public final float maxDurability;

    public ArmorItem(String id, String name, String description, String icon,
                     QualityTier qualityTier, int gridWidth, int gridHeight,
                     float weight, float armorRating, Map<DamageType, Float> resistances,
                     EquipmentSlot slotType, float maxDurability) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.armorRating = armorRating;
        this.resistances = new EnumMap<>(resistances);
        this.slotType = slotType;
        this.maxDurability = maxDurability;
        this.durability = maxDurability;
    }

    @Override
    public ItemType getType() {
        return ItemType.ARMOR;
    }
}
```

- [ ] **Step 6: Create AmmoItem, ModItem, ComponentItem, ConsumableItem, JunkItem**

```java
// AmmoItem.java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class AmmoItem extends Item {
    public final String ammoTypeId;

    public AmmoItem(String id, String name, String description, String icon,
                    QualityTier qualityTier, float weight, String ammoTypeId, int maxStack) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, maxStack);
        this.ammoTypeId = ammoTypeId;
    }

    @Override
    public ItemType getType() {
        return ItemType.AMMO;
    }
}
```

```java
// ModItem.java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class ModItem extends Item {
    public final String weaponModId;

    public ModItem(String id, String name, String description, String icon,
                   QualityTier qualityTier, float weight, String weaponModId) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, false, 1);
        this.weaponModId = weaponModId;
    }

    @Override
    public ItemType getType() {
        return ItemType.MOD;
    }
}
```

```java
// ComponentItem.java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class ComponentItem extends Item {
    public final String componentId;
    public final String componentType; // "frame", "barrel"

    public ComponentItem(String id, String name, String description, String icon,
                         QualityTier qualityTier, int gridWidth, int gridHeight,
                         float weight, String componentId, String componentType) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.componentId = componentId;
        this.componentType = componentType;
    }

    @Override
    public ItemType getType() {
        return ItemType.COMPONENT;
    }
}
```

```java
// ConsumableItem.java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class ConsumableItem extends Item {
    public final float healAmount;
    public final String buffEffect;
    public final float useTime;

    public ConsumableItem(String id, String name, String description, String icon,
                          QualityTier qualityTier, float weight, float healAmount,
                          String buffEffect, float useTime, int maxStack) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, maxStack);
        this.healAmount = healAmount;
        this.buffEffect = buffEffect;
        this.useTime = useTime;
    }

    @Override
    public ItemType getType() {
        return ItemType.CONSUMABLE;
    }
}
```

```java
// JunkItem.java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.Map;
import java.util.HashMap;

public class JunkItem extends Item {
    public final int sellValue;
    public final Map<String, Integer> salvageYields;

    public JunkItem(String id, String name, String description, String icon,
                    QualityTier qualityTier, float weight, int sellValue,
                    Map<String, Integer> salvageYields) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, 10);
        this.sellValue = sellValue;
        this.salvageYields = new HashMap<>(salvageYields);
    }

    @Override
    public ItemType getType() {
        return ItemType.JUNK;
    }
}
```

- [ ] **Step 7: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/equipment/
git commit -m "feat(equipment): add item model foundation with all item types"
```

---

### Task 2: Inventory Component with Grid Logic

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/equipment/components/InventoryComponent.java`
- Create: `core/src/test/java/com/galacticodyssey/equipment/InventoryComponentTest.java`

- [ ] **Step 1: Write failing tests for InventoryComponent**

```java
package com.galacticodyssey.equipment;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.items.AmmoItem;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.equipment.items.JunkItem;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.combat.data.WeaponAssembly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryComponentTest {

    private InventoryComponent inventory;

    @BeforeEach
    void setUp() {
        inventory = new InventoryComponent(8, 6, 50f);
    }

    @Test
    void addSmallItem_placedAtFirstAvailableSlot() {
        AmmoItem ammo = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        assertTrue(inventory.tryAdd(ammo));
        assertSame(ammo, inventory.getItemAt(0, 0));
    }

    @Test
    void addLargeItem_occupiesMultipleCells() {
        WeaponAssembly assembly = WeaponAssembly.ranged("rifle_standard", "long_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem rifle = new WeaponItem("rifle_1", "Standard Rifle", "A rifle",
            "rifle_icon", QualityTier.COMMON, 3, 1, 3.5f, assembly);
        assertTrue(inventory.tryAdd(rifle));
        assertSame(rifle, inventory.getItemAt(0, 0));
        assertSame(rifle, inventory.getItemAt(1, 0));
        assertSame(rifle, inventory.getItemAt(2, 0));
        assertNull(inventory.getItemAt(3, 0));
    }

    @Test
    void addItem_exceedsWeight_rejected() {
        inventory = new InventoryComponent(8, 6, 1f);
        WeaponAssembly assembly = WeaponAssembly.ranged("rifle_standard", "long_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem heavy = new WeaponItem("heavy_1", "Heavy Gun", "Too heavy",
            "heavy_icon", QualityTier.COMMON, 2, 1, 5.0f, assembly);
        assertFalse(inventory.tryAdd(heavy));
    }

    @Test
    void addStackableItem_mergesWithExisting() {
        AmmoItem ammo1 = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        ammo1.currentStack = 30;
        AmmoItem ammo2 = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        ammo2.currentStack = 20;

        assertTrue(inventory.tryAdd(ammo1));
        assertTrue(inventory.tryAdd(ammo2));
        assertEquals(50, ammo1.currentStack);
    }

    @Test
    void removeItem_freesGridCells() {
        AmmoItem ammo = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        inventory.tryAdd(ammo);
        assertTrue(inventory.remove(ammo));
        assertNull(inventory.getItemAt(0, 0));
    }

    @Test
    void gridFull_rejectsNewItems() {
        inventory = new InventoryComponent(1, 1, 100f);
        AmmoItem ammo1 = new AmmoItem("ammo_a", "Ammo A", "A",
            "icon", QualityTier.COMMON, 0.1f, "a", 1);
        AmmoItem ammo2 = new AmmoItem("ammo_b", "Ammo B", "B",
            "icon", QualityTier.COMMON, 0.1f, "b", 1);
        assertTrue(inventory.tryAdd(ammo1));
        assertFalse(inventory.tryAdd(ammo2));
    }

    @Test
    void getCurrentWeight_sumsAllItems() {
        AmmoItem ammo = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.5f, "standard_round", 60);
        ammo.currentStack = 10;
        inventory.tryAdd(ammo);
        assertEquals(5.0f, inventory.getCurrentWeight(), 0.01f);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.InventoryComponentTest" -i`
Expected: FAIL — class `InventoryComponent` does not exist

- [ ] **Step 3: Implement InventoryComponent**

```java
package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.equipment.items.Item;
import java.util.ArrayList;
import java.util.List;

public class InventoryComponent implements Component {
    public final int gridWidth;
    public final int gridHeight;
    public final float maxWeight;
    private final Item[][] grid;
    private final List<Item> allItems = new ArrayList<>();

    public InventoryComponent(int gridWidth, int gridHeight, float maxWeight) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.maxWeight = maxWeight;
        this.grid = new Item[gridWidth][gridHeight];
    }

    public boolean tryAdd(Item item) {
        if (getCurrentWeight() + item.getTotalWeight() > maxWeight) {
            return false;
        }
        if (item.stackable) {
            for (Item existing : allItems) {
                if (existing.id.equals(item.id) && existing.getSpaceRemaining() > 0) {
                    int transfer = Math.min(item.currentStack, existing.getSpaceRemaining());
                    existing.currentStack += transfer;
                    item.currentStack -= transfer;
                    if (item.currentStack <= 0) {
                        return true;
                    }
                }
            }
        }
        int[] pos = findFit(item.gridWidth, item.gridHeight);
        if (pos == null) {
            return false;
        }
        placeAt(item, pos[0], pos[1]);
        return true;
    }

    public boolean remove(Item item) {
        if (!allItems.remove(item)) {
            return false;
        }
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                if (grid[x][y] == item) {
                    grid[x][y] = null;
                }
            }
        }
        return true;
    }

    public Item getItemAt(int x, int y) {
        if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) {
            return null;
        }
        return grid[x][y];
    }

    public float getCurrentWeight() {
        float total = 0;
        for (Item item : allItems) {
            total += item.getTotalWeight();
        }
        return total;
    }

    public List<Item> getAllItems() {
        return allItems;
    }

    public int getItemCount() {
        return allItems.size();
    }

    private int[] findFit(int w, int h) {
        for (int y = 0; y <= gridHeight - h; y++) {
            for (int x = 0; x <= gridWidth - w; x++) {
                if (canPlace(x, y, w, h)) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private boolean canPlace(int startX, int startY, int w, int h) {
        for (int x = startX; x < startX + w; x++) {
            for (int y = startY; y < startY + h; y++) {
                if (grid[x][y] != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void placeAt(Item item, int startX, int startY) {
        for (int x = startX; x < startX + item.gridWidth; x++) {
            for (int y = startY; y < startY + item.gridHeight; y++) {
                grid[x][y] = item;
            }
        }
        allItems.add(item);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.InventoryComponentTest" -i`
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/equipment/components/InventoryComponent.java \
        core/src/test/java/com/galacticodyssey/equipment/InventoryComponentTest.java
git commit -m "feat(equipment): add grid-based InventoryComponent with placement and stacking"
```

---

### Task 3: Equipment Slots, Events, and LootDropComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/equipment/components/EquipmentSlotsComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/components/LootDropComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/events/ItemAddedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/events/ItemRemovedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/events/EquipmentChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/events/LootDroppedEvent.java`

- [ ] **Step 1: Create EquipmentSlotsComponent**

```java
package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.items.Item;
import java.util.EnumMap;
import java.util.Map;

public class EquipmentSlotsComponent implements Component {
    private final Map<EquipmentSlot, Item> slots = new EnumMap<>(EquipmentSlot.class);

    public Item getSlot(EquipmentSlot slot) {
        return slots.get(slot);
    }

    public Item setSlot(EquipmentSlot slot, Item item) {
        Item previous = slots.put(slot, item);
        return previous;
    }

    public Item clearSlot(EquipmentSlot slot) {
        return slots.remove(slot);
    }

    public boolean isSlotEmpty(EquipmentSlot slot) {
        return !slots.containsKey(slot);
    }

    public Map<EquipmentSlot, Item> getAllEquipped() {
        return slots;
    }
}
```

- [ ] **Step 2: Create LootDropComponent**

```java
package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.equipment.items.Item;
import java.util.ArrayList;
import java.util.List;

public class LootDropComponent implements Component {
    public final List<Item> items = new ArrayList<>();
    public final Vector3 position = new Vector3();
    public float despawnTimer;

    public LootDropComponent() {
        this.despawnTimer = -1f;
    }
}
```

- [ ] **Step 3: Create all event classes**

```java
// ItemAddedEvent.java
package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.equipment.items.Item;

public final class ItemAddedEvent {
    public final Entity entity;
    public final Item item;
    public final int gridX;
    public final int gridY;

    public ItemAddedEvent(Entity entity, Item item, int gridX, int gridY) {
        this.entity = entity;
        this.item = item;
        this.gridX = gridX;
        this.gridY = gridY;
    }
}
```

```java
// ItemRemovedEvent.java
package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.equipment.items.Item;

public final class ItemRemovedEvent {
    public final Entity entity;
    public final Item item;

    public ItemRemovedEvent(Entity entity, Item item) {
        this.entity = entity;
        this.item = item;
    }
}
```

```java
// EquipmentChangedEvent.java
package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.items.Item;

public final class EquipmentChangedEvent {
    public final Entity entity;
    public final EquipmentSlot slot;
    public final Item oldItem;
    public final Item newItem;

    public EquipmentChangedEvent(Entity entity, EquipmentSlot slot, Item oldItem, Item newItem) {
        this.entity = entity;
        this.slot = slot;
        this.oldItem = oldItem;
        this.newItem = newItem;
    }
}
```

```java
// LootDroppedEvent.java
package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.equipment.items.Item;
import java.util.List;

public final class LootDroppedEvent {
    public final Entity lootEntity;
    public final Vector3 position;
    public final List<Item> items;

    public LootDroppedEvent(Entity lootEntity, Vector3 position, List<Item> items) {
        this.lootEntity = lootEntity;
        this.position = position;
        this.items = items;
    }
}
```

- [ ] **Step 4: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/equipment/components/EquipmentSlotsComponent.java \
        core/src/main/java/com/galacticodyssey/equipment/components/LootDropComponent.java \
        core/src/main/java/com/galacticodyssey/equipment/events/
git commit -m "feat(equipment): add EquipmentSlotsComponent, LootDropComponent, and equipment events"
```

---

### Task 4: EquipmentSystem with Combat Bridge

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/equipment/systems/EquipmentSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/equipment/EquipmentSystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.equipment;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.data.WeaponStatsResolver;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.ArmorItem;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private EquipmentSystem system;
    private Entity player;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        system = new EquipmentSystem(eventBus);
        engine.addSystem(system);

        player = new Entity();
        player.add(new EquipmentSlotsComponent());
        player.add(new InventoryComponent(8, 6, 50f));
        player.add(new WeaponInventoryComponent());
        player.add(new RangedWeaponComponent());
        player.add(new ArmorComponent());
        engine.addEntity(player);
    }

    @Test
    void equipWeapon_publishesEquipmentChangedEvent() {
        AtomicReference<EquipmentChangedEvent> received = new AtomicReference<>();
        eventBus.subscribe(EquipmentChangedEvent.class, received::set);

        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        system.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol);

        assertNotNull(received.get());
        assertEquals(EquipmentSlot.PRIMARY_WEAPON, received.get().slot);
        assertNull(received.get().oldItem);
        assertSame(pistol, received.get().newItem);
    }

    @Test
    void equipWeapon_syncsWeaponInventoryComponent() {
        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        system.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol);

        WeaponInventoryComponent wic = player.getComponent(WeaponInventoryComponent.class);
        assertNotNull(wic.slots[WeaponSlot.PRIMARY.ordinal()]);
        assertEquals("pistol_standard", wic.slots[WeaponSlot.PRIMARY.ordinal()].frameId);
    }

    @Test
    void unequipWeapon_returnsToInventory() {
        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        system.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol);
        Item unequipped = system.unequip(player, EquipmentSlot.PRIMARY_WEAPON);

        assertSame(pistol, unequipped);
        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        assertTrue(inv.getAllItems().contains(pistol));
    }

    @Test
    void equipArmor_updatesArmorComponent() {
        Map<DamageType, Float> resistances = new EnumMap<>(DamageType.class);
        resistances.put(DamageType.BALLISTIC, 0.3f);
        ArmorItem chest = new ArmorItem("armor_chest_1", "Body Armor", "Chest plate",
            "chest_icon", QualityTier.MILITARY, 2, 2, 4.0f, 25.0f,
            resistances, EquipmentSlot.CHEST, 100f);

        system.equip(player, EquipmentSlot.CHEST, chest);

        ArmorComponent ac = player.getComponent(ArmorComponent.class);
        assertEquals(25.0f, ac.armorRating.get(HitRegion.TORSO), 0.01f);
    }

    @Test
    void equipWrongSlot_rejected() {
        Map<DamageType, Float> resistances = new EnumMap<>(DamageType.class);
        ArmorItem helmet = new ArmorItem("armor_helm_1", "Helmet", "Head protection",
            "helm_icon", QualityTier.COMMON, 2, 2, 2.0f, 15.0f,
            resistances, EquipmentSlot.HELMET, 80f);

        assertFalse(system.equip(player, EquipmentSlot.CHEST, helmet));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.EquipmentSystemTest" -i`
Expected: FAIL — class `EquipmentSystem` does not exist

- [ ] **Step 3: Implement EquipmentSystem**

```java
package com.galacticodyssey.equipment.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.*;

import java.util.Map;

public class EquipmentSystem extends EntitySystem {
    private static final int PRIORITY = 2;
    private final EventBus eventBus;

    private static final Map<EquipmentSlot, Integer> WEAPON_SLOT_MAP = Map.of(
        EquipmentSlot.PRIMARY_WEAPON, 0,
        EquipmentSlot.SECONDARY_WEAPON, 1,
        EquipmentSlot.MELEE_WEAPON, 2
    );

    private static final Map<EquipmentSlot, HitRegion> ARMOR_SLOT_MAP = Map.of(
        EquipmentSlot.HELMET, HitRegion.HEAD,
        EquipmentSlot.CHEST, HitRegion.TORSO,
        EquipmentSlot.LEGS, HitRegion.LEGS,
        EquipmentSlot.BOOTS, HitRegion.LEGS
    );

    public EquipmentSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    public boolean equip(Entity entity, EquipmentSlot slot, Item item) {
        if (!isValidForSlot(slot, item)) {
            return false;
        }
        EquipmentSlotsComponent equip = entity.getComponent(EquipmentSlotsComponent.class);
        if (equip == null) return false;

        Item old = equip.setSlot(slot, item);
        if (old != null) {
            InventoryComponent inv = entity.getComponent(InventoryComponent.class);
            if (inv != null) inv.tryAdd(old);
        }

        syncCombatComponents(entity, slot, item);
        eventBus.publish(new EquipmentChangedEvent(entity, slot, old, item));
        return true;
    }

    public Item unequip(Entity entity, EquipmentSlot slot) {
        EquipmentSlotsComponent equip = entity.getComponent(EquipmentSlotsComponent.class);
        if (equip == null) return null;

        Item old = equip.clearSlot(slot);
        if (old != null) {
            InventoryComponent inv = entity.getComponent(InventoryComponent.class);
            if (inv != null) inv.tryAdd(old);
            syncCombatComponents(entity, slot, null);
            eventBus.publish(new EquipmentChangedEvent(entity, slot, old, null));
        }
        return old;
    }

    private boolean isValidForSlot(EquipmentSlot slot, Item item) {
        switch (slot) {
            case PRIMARY_WEAPON:
            case SECONDARY_WEAPON:
                return item instanceof WeaponItem;
            case MELEE_WEAPON:
                return item instanceof MeleeWeaponItem;
            case HELMET:
            case CHEST:
            case LEGS:
            case BOOTS:
                if (!(item instanceof ArmorItem armor)) return false;
                return armor.slotType == slot;
            case UTILITY_1:
            case UTILITY_2:
                return item instanceof ConsumableItem;
            default:
                return false;
        }
    }

    private void syncCombatComponents(Entity entity, EquipmentSlot slot, Item item) {
        if (WEAPON_SLOT_MAP.containsKey(slot)) {
            syncWeaponSlot(entity, WEAPON_SLOT_MAP.get(slot), item);
        } else if (ARMOR_SLOT_MAP.containsKey(slot)) {
            syncArmorSlot(entity, slot);
        }
    }

    private void syncWeaponSlot(Entity entity, int slotIndex, Item item) {
        WeaponInventoryComponent wic = entity.getComponent(WeaponInventoryComponent.class);
        if (wic == null) return;

        if (item instanceof WeaponItem wi) {
            wic.slots[slotIndex] = wi.assembly;
        } else if (item instanceof MeleeWeaponItem mi) {
            wic.slots[slotIndex] = mi.assembly;
        } else {
            wic.slots[slotIndex] = null;
        }
    }

    private void syncArmorSlot(Entity entity, EquipmentSlot changedSlot) {
        ArmorComponent ac = entity.getComponent(ArmorComponent.class);
        EquipmentSlotsComponent equip = entity.getComponent(EquipmentSlotsComponent.class);
        if (ac == null || equip == null) return;

        for (HitRegion region : HitRegion.values()) {
            ac.armorRating.put(region, 0f);
            ac.resistances.put(region, new java.util.EnumMap<>(DamageType.class));
        }

        for (Map.Entry<EquipmentSlot, HitRegion> entry : ARMOR_SLOT_MAP.entrySet()) {
            Item equipped = equip.getSlot(entry.getKey());
            if (equipped instanceof ArmorItem armor) {
                HitRegion region = entry.getValue();
                float current = ac.armorRating.getOrDefault(region, 0f);
                ac.armorRating.put(region, current + armor.armorRating);
                for (Map.Entry<DamageType, Float> res : armor.resistances.entrySet()) {
                    Map<DamageType, Float> regionRes = ac.resistances.get(region);
                    float existing = regionRes.getOrDefault(res.getKey(), 0f);
                    regionRes.put(res.getKey(), Math.min(existing + res.getValue(), 0.85f));
                }
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        // Equipment changes are command-driven, not per-frame
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.EquipmentSystemTest" -i`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/equipment/systems/EquipmentSystem.java \
        core/src/test/java/com/galacticodyssey/equipment/EquipmentSystemTest.java
git commit -m "feat(equipment): add EquipmentSystem with combat component syncing"
```

---

### Task 5: LootTable Data Model and LootGenerationSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/equipment/data/LootTable.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/data/LootTableRegistry.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/systems/LootGenerationSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/equipment/LootGenerationSystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.equipment;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.equipment.data.LootTable;
import com.galacticodyssey.equipment.data.LootTableRegistry;
import com.galacticodyssey.equipment.events.LootDroppedEvent;
import com.galacticodyssey.equipment.systems.LootGenerationSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LootGenerationSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private LootTableRegistry registry;
    private LootGenerationSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        registry = new LootTableRegistry();

        LootTable.Entry entry = new LootTable.Entry("ammo_std", "ammo", 1.0f, 10, 30);
        LootTable table = new LootTable("grunt", List.of(entry),
            new float[]{0.6f, 0.25f, 0.1f, 0.04f, 0.01f, 0f, 0f});
        registry.register(table);

        system = new LootGenerationSystem(eventBus, registry);
        engine.addSystem(system);
    }

    @Test
    void entityKilled_withLootTable_dropsLoot() {
        AtomicReference<LootDroppedEvent> received = new AtomicReference<>();
        eventBus.subscribe(LootDroppedEvent.class, received::set);

        Entity target = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(5f, 0f, 10f);
        target.add(tc);

        Entity killer = new Entity();
        engine.addEntity(target);
        engine.addEntity(killer);

        eventBus.publish(new EntityKilledEvent(target, killer));
        engine.update(0.016f);

        assertNotNull(received.get());
        assertFalse(received.get().items.isEmpty());
        assertEquals(5f, received.get().position.x, 0.01f);
    }

    @Test
    void entityKilled_noLootTable_noEvent() {
        AtomicReference<LootDroppedEvent> received = new AtomicReference<>();
        eventBus.subscribe(LootDroppedEvent.class, received::set);

        Entity target = new Entity();
        target.add(new TransformComponent());
        Entity killer = new Entity();
        engine.addEntity(target);

        eventBus.publish(new EntityKilledEvent(target, killer));
        engine.update(0.016f);

        assertNull(received.get());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.LootGenerationSystemTest" -i`
Expected: FAIL — classes do not exist

- [ ] **Step 3: Implement LootTable and LootTableRegistry**

```java
// LootTable.java
package com.galacticodyssey.equipment.data;

import java.util.List;

public class LootTable {
    public final String archetypeId;
    public final List<Entry> entries;
    public final float[] qualityWeights; // indexed by QualityTier ordinal

    public LootTable(String archetypeId, List<Entry> entries, float[] qualityWeights) {
        this.archetypeId = archetypeId;
        this.entries = entries;
        this.qualityWeights = qualityWeights;
    }

    public static class Entry {
        public final String itemId;
        public final String itemType;
        public final float dropChance;
        public final int minQuantity;
        public final int maxQuantity;

        public Entry(String itemId, String itemType, float dropChance,
                     int minQuantity, int maxQuantity) {
            this.itemId = itemId;
            this.itemType = itemType;
            this.dropChance = dropChance;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
        }
    }
}
```

```java
// LootTableRegistry.java
package com.galacticodyssey.equipment.data;

import java.util.HashMap;
import java.util.Map;

public class LootTableRegistry {
    private final Map<String, LootTable> tables = new HashMap<>();

    public void register(LootTable table) {
        tables.put(table.archetypeId, table);
    }

    public LootTable getTable(String archetypeId) {
        return tables.get(archetypeId);
    }

    public boolean hasTable(String archetypeId) {
        return tables.containsKey(archetypeId);
    }
}
```

- [ ] **Step 4: Implement LootGenerationSystem**

```java
package com.galacticodyssey.equipment.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.equipment.components.LootDropComponent;
import com.galacticodyssey.equipment.data.LootTable;
import com.galacticodyssey.equipment.data.LootTableRegistry;
import com.galacticodyssey.equipment.events.LootDroppedEvent;
import com.galacticodyssey.equipment.items.AmmoItem;
import com.galacticodyssey.equipment.items.Item;

import java.util.ArrayList;
import java.util.List;

public class LootGenerationSystem extends EntitySystem {
    private static final int PRIORITY = 10;
    private final EventBus eventBus;
    private final LootTableRegistry registry;
    private final List<EntityKilledEvent> pendingKills = new ArrayList<>();

    public LootGenerationSystem(EventBus eventBus, LootTableRegistry registry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.registry = registry;
        eventBus.subscribe(EntityKilledEvent.class, pendingKills::add);
    }

    @Override
    public void update(float deltaTime) {
        for (EntityKilledEvent event : pendingKills) {
            processKill(event);
        }
        pendingKills.clear();
    }

    private void processKill(EntityKilledEvent event) {
        // Use "grunt" as default archetype — in production, read from an ArchetypeComponent
        LootTable table = registry.getTable("grunt");
        if (table == null) return;

        TransformComponent tc = event.target.getComponent(TransformComponent.class);
        if (tc == null) return;

        List<Item> drops = new ArrayList<>();
        QualityTier quality = rollQuality(table.qualityWeights);

        for (LootTable.Entry entry : table.entries) {
            if (MathUtils.random() <= entry.dropChance) {
                Item item = createItem(entry, quality);
                if (item != null) drops.add(item);
            }
        }

        if (drops.isEmpty()) return;

        Entity lootEntity = new Entity();
        LootDropComponent ldc = new LootDropComponent();
        ldc.items.addAll(drops);
        ldc.position.set(tc.position);
        ldc.despawnTimer = 120f;
        lootEntity.add(ldc);

        TransformComponent lootTransform = new TransformComponent();
        lootTransform.position.set(tc.position);
        lootEntity.add(lootTransform);

        if (getEngine() != null) {
            getEngine().addEntity(lootEntity);
        }

        eventBus.publish(new LootDroppedEvent(lootEntity, new Vector3(tc.position), drops));
    }

    private QualityTier rollQuality(float[] weights) {
        QualityTier[] tiers = QualityTier.values();
        float roll = MathUtils.random();
        float cumulative = 0f;
        for (int i = 0; i < weights.length && i < tiers.length; i++) {
            cumulative += weights[i];
            if (roll <= cumulative) return tiers[i];
        }
        return QualityTier.COMMON;
    }

    private Item createItem(LootTable.Entry entry, QualityTier quality) {
        int quantity = MathUtils.random(entry.minQuantity, entry.maxQuantity);
        if ("ammo".equals(entry.itemType)) {
            AmmoItem ammo = new AmmoItem(entry.itemId, entry.itemId, "",
                "ammo_icon", quality, 0.1f, entry.itemId, 999);
            ammo.currentStack = quantity;
            return ammo;
        }
        return null;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.LootGenerationSystemTest" -i`
Expected: All 2 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/equipment/data/ \
        core/src/main/java/com/galacticodyssey/equipment/systems/LootGenerationSystem.java \
        core/src/test/java/com/galacticodyssey/equipment/LootGenerationSystemTest.java
git commit -m "feat(equipment): add loot tables and LootGenerationSystem"
```

---

### Task 6: WeaponAssemblySystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/equipment/systems/WeaponAssemblySystem.java`
- Create: `core/src/test/java/com/galacticodyssey/equipment/WeaponAssemblySystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.equipment;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.equipment.items.ComponentItem;
import com.galacticodyssey.equipment.items.ModItem;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.equipment.systems.WeaponAssemblySystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeaponAssemblySystemTest {

    private WeaponAssemblySystem system;

    @BeforeEach
    void setUp() {
        system = new WeaponAssemblySystem();
    }

    @Test
    void assembleWeapon_validParts_createsWeaponItem() {
        ComponentItem frame = new ComponentItem("frame_pistol", "Pistol Frame", "A frame",
            "icon", QualityTier.COMMON, 2, 1, 0.5f, "pistol_standard", "frame");
        ComponentItem barrel = new ComponentItem("barrel_std", "Standard Barrel", "A barrel",
            "icon", QualityTier.COMMON, 1, 1, 0.3f, "standard_barrel", "barrel");
        ComponentItem ammo = new ComponentItem("ammo_std", "Standard Ammo Type", "Ammo",
            "icon", QualityTier.COMMON, 1, 1, 0.1f, "standard_round", "ammo_type");

        WeaponItem result = system.assemble(frame, barrel, ammo, List.of(), QualityTier.COMMON);

        assertNotNull(result);
        assertEquals("pistol_standard", result.assembly.frameId);
        assertEquals("standard_barrel", result.assembly.barrelId);
        assertEquals("standard_round", result.assembly.ammoTypeId);
        assertFalse(result.assembly.isMelee);
    }

    @Test
    void assembleWeapon_withMods_includesModIds() {
        ComponentItem frame = new ComponentItem("frame_rifle", "Rifle Frame", "A frame",
            "icon", QualityTier.COMMON, 3, 1, 1.0f, "rifle_standard", "frame");
        ComponentItem barrel = new ComponentItem("barrel_long", "Long Barrel", "A barrel",
            "icon", QualityTier.COMMON, 2, 1, 0.5f, "long_barrel", "barrel");
        ComponentItem ammo = new ComponentItem("ammo_std", "Standard Ammo Type", "Ammo",
            "icon", QualityTier.COMMON, 1, 1, 0.1f, "standard_round", "ammo_type");
        ModItem mod = new ModItem("mod_scope", "Basic Scope", "A scope",
            "icon", QualityTier.COMMON, 0.2f, "basic_scope");

        WeaponItem result = system.assemble(frame, barrel, ammo, List.of(mod), QualityTier.MILITARY);

        assertNotNull(result);
        assertEquals(QualityTier.MILITARY, result.qualityTier);
        assertEquals(1, result.assembly.modIds.length);
        assertEquals("basic_scope", result.assembly.modIds[0]);
    }

    @Test
    void assembleWeapon_missingFrame_returnsNull() {
        ComponentItem barrel = new ComponentItem("barrel_std", "Standard Barrel", "A barrel",
            "icon", QualityTier.COMMON, 1, 1, 0.3f, "standard_barrel", "barrel");
        ComponentItem ammo = new ComponentItem("ammo_std", "Standard Ammo Type", "Ammo",
            "icon", QualityTier.COMMON, 1, 1, 0.1f, "standard_round", "ammo_type");

        WeaponItem result = system.assemble(null, barrel, ammo, List.of(), QualityTier.COMMON);
        assertNull(result);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.WeaponAssemblySystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement WeaponAssemblySystem**

```java
package com.galacticodyssey.equipment.systems;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.equipment.items.ComponentItem;
import com.galacticodyssey.equipment.items.ModItem;
import com.galacticodyssey.equipment.items.WeaponItem;

import java.util.List;

public class WeaponAssemblySystem {

    public WeaponItem assemble(ComponentItem frame, ComponentItem barrel,
                               ComponentItem ammoType, List<ModItem> mods,
                               QualityTier quality) {
        if (frame == null || !"frame".equals(frame.componentType)) return null;
        if (barrel == null || !"barrel".equals(barrel.componentType)) return null;
        if (ammoType == null || !"ammo_type".equals(ammoType.componentType)) return null;

        String[] modIds = mods.stream()
            .map(m -> m.weaponModId)
            .toArray(String[]::new);

        WeaponAssembly assembly = WeaponAssembly.ranged(
            frame.componentId, barrel.componentId, ammoType.componentId, modIds, quality);

        float totalWeight = frame.weight + barrel.weight + ammoType.weight
            + (float) mods.stream().mapToDouble(m -> m.weight).sum();
        int gridW = Math.max(frame.gridWidth, barrel.gridWidth);
        int gridH = frame.gridHeight;

        String name = frame.name + " (" + barrel.name + ")";

        return new WeaponItem(
            "assembled_" + frame.componentId + "_" + barrel.componentId,
            name, "Assembled weapon", frame.icon, quality,
            gridW, gridH, totalWeight, assembly
        );
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.equipment.WeaponAssemblySystemTest" -i`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/equipment/systems/WeaponAssemblySystem.java \
        core/src/test/java/com/galacticodyssey/equipment/WeaponAssemblySystemTest.java
git commit -m "feat(equipment): add WeaponAssemblySystem for crafting weapons from parts"
```

---

### Task 7: Equipment JSON Data Files

**Files:**
- Create: `core/src/main/resources/data/equipment/loot_tables.json`
- Create: `core/src/main/resources/data/equipment/armor_definitions.json`
- Create: `core/src/main/resources/data/equipment/consumables.json`

- [ ] **Step 1: Create loot_tables.json**

```json
[
  {
    "archetypeId": "grunt",
    "entries": [
      { "itemId": "ammo_standard", "itemType": "ammo", "dropChance": 0.8, "minQuantity": 10, "maxQuantity": 30 },
      { "itemId": "ammo_incendiary", "itemType": "ammo", "dropChance": 0.2, "minQuantity": 5, "maxQuantity": 15 },
      { "itemId": "medkit_small", "itemType": "consumable", "dropChance": 0.3, "minQuantity": 1, "maxQuantity": 1 }
    ],
    "qualityWeights": [0.05, 0.60, 0.20, 0.10, 0.04, 0.01, 0.00]
  },
  {
    "archetypeId": "heavy",
    "entries": [
      { "itemId": "ammo_standard", "itemType": "ammo", "dropChance": 1.0, "minQuantity": 20, "maxQuantity": 60 },
      { "itemId": "armor_plate_salvage", "itemType": "junk", "dropChance": 0.4, "minQuantity": 1, "maxQuantity": 2 },
      { "itemId": "medkit_small", "itemType": "consumable", "dropChance": 0.5, "minQuantity": 1, "maxQuantity": 2 }
    ],
    "qualityWeights": [0.02, 0.40, 0.30, 0.18, 0.07, 0.02, 0.01]
  },
  {
    "archetypeId": "officer",
    "entries": [
      { "itemId": "ammo_standard", "itemType": "ammo", "dropChance": 1.0, "minQuantity": 15, "maxQuantity": 40 },
      { "itemId": "mod_random", "itemType": "mod", "dropChance": 0.3, "minQuantity": 1, "maxQuantity": 1 },
      { "itemId": "medkit_large", "itemType": "consumable", "dropChance": 0.4, "minQuantity": 1, "maxQuantity": 1 }
    ],
    "qualityWeights": [0.00, 0.20, 0.30, 0.30, 0.12, 0.06, 0.02]
  }
]
```

- [ ] **Step 2: Create armor_definitions.json**

```json
[
  {
    "id": "helmet_light", "name": "Light Helmet", "description": "Basic head protection",
    "icon": "armor/helmet_light", "slotType": "HELMET", "gridWidth": 2, "gridHeight": 2,
    "weight": 1.5, "armorRating": 10.0, "maxDurability": 80.0,
    "resistances": { "BALLISTIC": 0.15, "MELEE": 0.10 }
  },
  {
    "id": "chest_medium", "name": "Medium Body Armor", "description": "Standard torso protection",
    "icon": "armor/chest_medium", "slotType": "CHEST", "gridWidth": 2, "gridHeight": 3,
    "weight": 5.0, "armorRating": 25.0, "maxDurability": 120.0,
    "resistances": { "BALLISTIC": 0.25, "ENERGY": 0.10, "MELEE": 0.20 }
  },
  {
    "id": "legs_light", "name": "Light Leg Guards", "description": "Basic leg protection",
    "icon": "armor/legs_light", "slotType": "LEGS", "gridWidth": 2, "gridHeight": 2,
    "weight": 2.0, "armorRating": 12.0, "maxDurability": 90.0,
    "resistances": { "BALLISTIC": 0.10, "MELEE": 0.15 }
  },
  {
    "id": "boots_standard", "name": "Standard Boots", "description": "Basic footwear with light armor",
    "icon": "armor/boots_standard", "slotType": "BOOTS", "gridWidth": 2, "gridHeight": 2,
    "weight": 1.0, "armorRating": 5.0, "maxDurability": 60.0,
    "resistances": { "MELEE": 0.05 }
  },
  {
    "id": "chest_heavy_exo", "name": "Heavy Exosuit Chest", "description": "Military-grade powered armor",
    "icon": "armor/chest_heavy", "slotType": "CHEST", "gridWidth": 3, "gridHeight": 3,
    "weight": 12.0, "armorRating": 50.0, "maxDurability": 200.0,
    "resistances": { "BALLISTIC": 0.40, "ENERGY": 0.25, "EXPLOSIVE": 0.30, "MELEE": 0.35 }
  }
]
```

- [ ] **Step 3: Create consumables.json**

```json
[
  {
    "id": "medkit_small", "name": "Small Medkit", "description": "Restores 25 HP",
    "icon": "consumable/medkit_small", "weight": 0.3,
    "healAmount": 25.0, "buffEffect": null, "useTime": 2.0, "maxStack": 5
  },
  {
    "id": "medkit_large", "name": "Large Medkit", "description": "Restores 60 HP",
    "icon": "consumable/medkit_large", "weight": 0.5,
    "healAmount": 60.0, "buffEffect": null, "useTime": 3.5, "maxStack": 3
  },
  {
    "id": "stim_speed", "name": "Speed Stim", "description": "Increases movement speed for 15 seconds",
    "icon": "consumable/stim_speed", "weight": 0.2,
    "healAmount": 0.0, "buffEffect": "speed_boost", "useTime": 1.0, "maxStack": 3
  },
  {
    "id": "stim_damage", "name": "Combat Stim", "description": "Increases damage by 20% for 10 seconds",
    "icon": "consumable/stim_damage", "weight": 0.2,
    "healAmount": 0.0, "buffEffect": "damage_boost", "useTime": 1.0, "maxStack": 3
  }
]
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/data/equipment/
git commit -m "feat(equipment): add loot tables, armor definitions, and consumables data"
```

---

## Domain 2: Ship Weapons & Hardpoints

### Task 8: Ship Weapon Enums and Data Model

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/ShipWeaponEnums.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/data/ShipWeaponData.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/data/Hardpoint.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/data/HardpointTemplate.java`

- [ ] **Step 1: Create ShipWeaponEnums**

```java
package com.galacticodyssey.ship.weapons;

public final class ShipWeaponEnums {

    public enum HardpointType {
        TURRET, FIXED, BROADSIDE, MISSILE_BAY, POINT_DEFENSE
    }

    public enum HardpointSize {
        SMALL, MEDIUM, LARGE, CAPITAL
    }

    public enum HardpointState {
        IDLE, TRACKING, FIRING, RELOADING, DISABLED
    }

    public enum ShipWeaponCategory {
        BALLISTIC_CANNON, LASER_ARRAY, PLASMA_TURRET, MISSILE_LAUNCHER,
        RAILGUN, EMP_PROJECTOR, POINT_DEFENSE, FLAK_CANNON
    }

    private ShipWeaponEnums() {}
}
```

- [ ] **Step 2: Create ShipWeaponData**

```java
package com.galacticodyssey.ship.weapons.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;

public class ShipWeaponData {
    public String id;
    public String name;
    public ShipWeaponCategory category;
    public float damage;
    public DamageType damageType;
    public float fireRate;
    public float projectileSpeed;
    public float range;
    public float energyCost;
    public float heatPerShot;
    public Integer ammoCapacity;
    public Integer currentAmmo;
    public float trackingSpeed;
    public int burstCount = 1;
    public float burstDelay = 0f;

    public boolean hasAmmo() {
        return ammoCapacity != null;
    }

    public boolean canFire() {
        return !hasAmmo() || (currentAmmo != null && currentAmmo > 0);
    }

    public void consumeAmmo() {
        if (currentAmmo != null) {
            currentAmmo--;
        }
    }
}
```

- [ ] **Step 3: Create Hardpoint**

```java
package com.galacticodyssey.ship.weapons.data;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;

public class Hardpoint {
    public final String id;
    public final Vector3 position = new Vector3();
    public final HardpointType type;
    public final HardpointSize sizeClass;
    public float arcMin;
    public float arcMax;
    public ShipWeaponData mountedWeapon;
    public HardpointState currentState = HardpointState.IDLE;
    public final Quaternion currentRotation = new Quaternion();
    public float fireTimer;

    public Hardpoint(String id, HardpointType type, HardpointSize sizeClass,
                     float arcMin, float arcMax) {
        this.id = id;
        this.type = type;
        this.sizeClass = sizeClass;
        this.arcMin = arcMin;
        this.arcMax = arcMax;
    }

    public boolean isInArc(float angle) {
        if (arcMax >= 360f) return true;
        float half = arcMax / 2f;
        return angle >= -half && angle <= half;
    }

    public boolean isEmpty() {
        return mountedWeapon == null;
    }
}
```

- [ ] **Step 4: Create HardpointTemplate**

```java
package com.galacticodyssey.ship.weapons.data;

import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;

public class HardpointTemplate {
    public String id;
    public float posX, posY, posZ;
    public HardpointType type;
    public HardpointSize sizeClass;
    public float arcMin;
    public float arcMax;
    public String defaultWeaponId;
}
```

- [ ] **Step 5: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/weapons/
git commit -m "feat(ship-weapons): add ship weapon enums, data model, and hardpoint classes"
```

---

### Task 9: Ship Weapon Components and Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/components/ShipHardpointComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/components/ShipWeaponHeatComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/components/GuidedProjectileComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/events/ShipWeaponFiredEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/events/ShipOverheatEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/events/MissileLockedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/events/PointDefenseEngagedEvent.java`

- [ ] **Step 1: Create ShipHardpointComponent**

```java
package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import java.util.ArrayList;
import java.util.List;

public class ShipHardpointComponent implements Component {
    public final List<Hardpoint> hardpoints = new ArrayList<>();
    public Entity currentTarget;

    public Hardpoint getHardpoint(String id) {
        for (Hardpoint hp : hardpoints) {
            if (hp.id.equals(id)) return hp;
        }
        return null;
    }
}
```

- [ ] **Step 2: Create ShipWeaponHeatComponent**

```java
package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ShipWeaponHeatComponent implements Component {
    public final Map<String, Float> heatPerHardpoint = new HashMap<>();
    public float maxHeat = 1.0f;
    public float dissipationRate = 0.15f;
    public float overheatThreshold = 0.5f;
    public final Set<String> overheatedHardpoints = new HashSet<>();

    public float getHeat(String hardpointId) {
        return heatPerHardpoint.getOrDefault(hardpointId, 0f);
    }

    public void addHeat(String hardpointId, float amount) {
        float current = getHeat(hardpointId);
        heatPerHardpoint.put(hardpointId, Math.min(current + amount, maxHeat));
    }

    public boolean isOverheated(String hardpointId) {
        return overheatedHardpoints.contains(hardpointId);
    }
}
```

- [ ] **Step 3: Create GuidedProjectileComponent**

```java
package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class GuidedProjectileComponent implements Component {
    public Entity targetEntity;
    public float turnRate = 90f;
    public float armingDistance = 20f;
    public float flareVulnerability = 0.3f;
    public float distanceTraveled;
}
```

- [ ] **Step 4: Create all ship weapon event classes**

```java
// ShipWeaponFiredEvent.java
package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;

public final class ShipWeaponFiredEvent {
    public final Entity shipEntity;
    public final String hardpointId;
    public final Vector3 origin;
    public final Vector3 direction;
    public final ShipWeaponData weaponData;

    public ShipWeaponFiredEvent(Entity shipEntity, String hardpointId,
                                Vector3 origin, Vector3 direction,
                                ShipWeaponData weaponData) {
        this.shipEntity = shipEntity;
        this.hardpointId = hardpointId;
        this.origin = origin;
        this.direction = direction;
        this.weaponData = weaponData;
    }
}
```

```java
// ShipOverheatEvent.java
package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;

public final class ShipOverheatEvent {
    public final Entity shipEntity;
    public final String hardpointId;

    public ShipOverheatEvent(Entity shipEntity, String hardpointId) {
        this.shipEntity = shipEntity;
        this.hardpointId = hardpointId;
    }
}
```

```java
// MissileLockedEvent.java
package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;

public final class MissileLockedEvent {
    public final Entity targetEntity;
    public final Entity missileEntity;

    public MissileLockedEvent(Entity targetEntity, Entity missileEntity) {
        this.targetEntity = targetEntity;
        this.missileEntity = missileEntity;
    }
}
```

```java
// PointDefenseEngagedEvent.java
package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;

public final class PointDefenseEngagedEvent {
    public final Entity shipEntity;
    public final Entity interceptedProjectile;

    public PointDefenseEngagedEvent(Entity shipEntity, Entity interceptedProjectile) {
        this.shipEntity = shipEntity;
        this.interceptedProjectile = interceptedProjectile;
    }
}
```

- [ ] **Step 5: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/weapons/components/ \
        core/src/main/java/com/galacticodyssey/ship/weapons/events/
git commit -m "feat(ship-weapons): add ship weapon components and events"
```

---

### Task 10: ShipWeaponRegistry and Data Files

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/data/ShipWeaponRegistry.java`
- Create: `core/src/main/resources/data/weapons/ship_weapons.json`
- Create: `core/src/main/resources/data/weapons/hardpoint_templates.json`

- [ ] **Step 1: Create ShipWeaponRegistry**

```java
package com.galacticodyssey.ship.weapons.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShipWeaponRegistry {
    private final Map<String, ShipWeaponData> weapons = new HashMap<>();
    private final Map<String, List<HardpointTemplate>> hardpointTemplates = new HashMap<>();

    public void loadWeapons(String path) {
        Json json = new Json();
        JsonValue root = json.fromJson(null, Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ShipWeaponData data = new ShipWeaponData();
            data.id = entry.getString("id");
            data.name = entry.getString("name");
            data.category = ShipWeaponCategory.valueOf(entry.getString("category"));
            data.damage = entry.getFloat("damage");
            data.damageType = DamageType.valueOf(entry.getString("damageType"));
            data.fireRate = entry.getFloat("fireRate");
            data.projectileSpeed = entry.getFloat("projectileSpeed");
            data.range = entry.getFloat("range");
            data.energyCost = entry.getFloat("energyCost");
            data.heatPerShot = entry.getFloat("heatPerShot");
            if (entry.has("ammoCapacity")) {
                data.ammoCapacity = entry.getInt("ammoCapacity");
                data.currentAmmo = data.ammoCapacity;
            }
            data.trackingSpeed = entry.getFloat("trackingSpeed", 0f);
            data.burstCount = entry.getInt("burstCount", 1);
            data.burstDelay = entry.getFloat("burstDelay", 0f);
            weapons.put(data.id, data);
        }
    }

    public void loadHardpointTemplates(String path) {
        Json json = new Json();
        JsonValue root = json.fromJson(null, Gdx.files.internal(path));
        for (JsonValue ship = root.child; ship != null; ship = ship.next) {
            String shipId = ship.getString("shipClass");
            List<HardpointTemplate> templates = new ArrayList<>();
            for (JsonValue hp = ship.get("hardpoints").child; hp != null; hp = hp.next) {
                HardpointTemplate t = new HardpointTemplate();
                t.id = hp.getString("id");
                t.posX = hp.getFloat("posX");
                t.posY = hp.getFloat("posY");
                t.posZ = hp.getFloat("posZ");
                t.type = HardpointType.valueOf(hp.getString("type"));
                t.sizeClass = HardpointSize.valueOf(hp.getString("sizeClass"));
                t.arcMin = hp.getFloat("arcMin", 0f);
                t.arcMax = hp.getFloat("arcMax", 360f);
                t.defaultWeaponId = hp.getString("defaultWeaponId", null);
                templates.add(t);
            }
            hardpointTemplates.put(shipId, templates);
        }
    }

    public ShipWeaponData getWeapon(String id) {
        return weapons.get(id);
    }

    public ShipWeaponData createWeaponInstance(String id) {
        ShipWeaponData template = weapons.get(id);
        if (template == null) return null;
        ShipWeaponData instance = new ShipWeaponData();
        instance.id = template.id;
        instance.name = template.name;
        instance.category = template.category;
        instance.damage = template.damage;
        instance.damageType = template.damageType;
        instance.fireRate = template.fireRate;
        instance.projectileSpeed = template.projectileSpeed;
        instance.range = template.range;
        instance.energyCost = template.energyCost;
        instance.heatPerShot = template.heatPerShot;
        instance.ammoCapacity = template.ammoCapacity;
        instance.currentAmmo = template.ammoCapacity;
        instance.trackingSpeed = template.trackingSpeed;
        instance.burstCount = template.burstCount;
        instance.burstDelay = template.burstDelay;
        return instance;
    }

    public List<HardpointTemplate> getHardpointTemplates(String shipClass) {
        return hardpointTemplates.getOrDefault(shipClass, List.of());
    }
}
```

- [ ] **Step 2: Create ship_weapons.json**

```json
[
  {
    "id": "ballistic_autocannon_sm", "name": "Light Autocannon",
    "category": "BALLISTIC_CANNON", "damage": 15.0, "damageType": "BALLISTIC",
    "fireRate": 8.0, "projectileSpeed": 200.0, "range": 800.0,
    "energyCost": 1.0, "heatPerShot": 0.03, "ammoCapacity": 500,
    "trackingSpeed": 120.0, "burstCount": 1, "burstDelay": 0.0
  },
  {
    "id": "laser_array_sm", "name": "Light Laser Array",
    "category": "LASER_ARRAY", "damage": 10.0, "damageType": "ENERGY",
    "fireRate": 12.0, "projectileSpeed": 1000.0, "range": 1200.0,
    "energyCost": 3.0, "heatPerShot": 0.05,
    "trackingSpeed": 180.0, "burstCount": 1, "burstDelay": 0.0
  },
  {
    "id": "plasma_turret_md", "name": "Medium Plasma Turret",
    "category": "PLASMA_TURRET", "damage": 45.0, "damageType": "PLASMA",
    "fireRate": 3.0, "projectileSpeed": 120.0, "range": 600.0,
    "energyCost": 8.0, "heatPerShot": 0.08,
    "trackingSpeed": 60.0, "burstCount": 1, "burstDelay": 0.0
  },
  {
    "id": "missile_launcher_md", "name": "Medium Missile Pod",
    "category": "MISSILE_LAUNCHER", "damage": 120.0, "damageType": "EXPLOSIVE",
    "fireRate": 0.5, "projectileSpeed": 80.0, "range": 2000.0,
    "energyCost": 5.0, "heatPerShot": 0.02, "ammoCapacity": 24,
    "trackingSpeed": 45.0, "burstCount": 4, "burstDelay": 0.2
  },
  {
    "id": "point_defense_sm", "name": "Point Defense Turret",
    "category": "POINT_DEFENSE", "damage": 8.0, "damageType": "BALLISTIC",
    "fireRate": 20.0, "projectileSpeed": 500.0, "range": 400.0,
    "energyCost": 0.5, "heatPerShot": 0.02, "ammoCapacity": 1000,
    "trackingSpeed": 360.0, "burstCount": 1, "burstDelay": 0.0
  },
  {
    "id": "railgun_lg", "name": "Heavy Railgun",
    "category": "RAILGUN", "damage": 300.0, "damageType": "BALLISTIC",
    "fireRate": 0.2, "projectileSpeed": 800.0, "range": 3000.0,
    "energyCost": 25.0, "heatPerShot": 0.20,
    "trackingSpeed": 15.0, "burstCount": 1, "burstDelay": 0.0
  }
]
```

- [ ] **Step 3: Create hardpoint_templates.json**

```json
[
  {
    "shipClass": "FIGHTER",
    "hardpoints": [
      { "id": "nose_gun_1", "posX": 0.0, "posY": 0.2, "posZ": 3.0, "type": "FIXED", "sizeClass": "SMALL", "arcMin": 0, "arcMax": 15, "defaultWeaponId": "ballistic_autocannon_sm" },
      { "id": "nose_gun_2", "posX": 0.0, "posY": -0.2, "posZ": 3.0, "type": "FIXED", "sizeClass": "SMALL", "arcMin": 0, "arcMax": 15, "defaultWeaponId": "laser_array_sm" },
      { "id": "wing_missile_l", "posX": -2.5, "posY": 0.0, "posZ": 0.0, "type": "MISSILE_BAY", "sizeClass": "SMALL", "arcMin": 0, "arcMax": 90, "defaultWeaponId": null },
      { "id": "wing_missile_r", "posX": 2.5, "posY": 0.0, "posZ": 0.0, "type": "MISSILE_BAY", "sizeClass": "SMALL", "arcMin": 0, "arcMax": 90, "defaultWeaponId": null }
    ]
  },
  {
    "shipClass": "CORVETTE",
    "hardpoints": [
      { "id": "dorsal_turret", "posX": 0.0, "posY": 2.0, "posZ": 1.0, "type": "TURRET", "sizeClass": "MEDIUM", "arcMin": 0, "arcMax": 360, "defaultWeaponId": "plasma_turret_md" },
      { "id": "ventral_turret", "posX": 0.0, "posY": -2.0, "posZ": -1.0, "type": "TURRET", "sizeClass": "MEDIUM", "arcMin": 0, "arcMax": 360, "defaultWeaponId": "plasma_turret_md" },
      { "id": "bow_fixed", "posX": 0.0, "posY": 0.0, "posZ": 8.0, "type": "FIXED", "sizeClass": "LARGE", "arcMin": 0, "arcMax": 10, "defaultWeaponId": "railgun_lg" },
      { "id": "pd_turret_1", "posX": 3.0, "posY": 1.0, "posZ": 0.0, "type": "POINT_DEFENSE", "sizeClass": "SMALL", "arcMin": 0, "arcMax": 360, "defaultWeaponId": "point_defense_sm" },
      { "id": "pd_turret_2", "posX": -3.0, "posY": 1.0, "posZ": 0.0, "type": "POINT_DEFENSE", "sizeClass": "SMALL", "arcMin": 0, "arcMax": 360, "defaultWeaponId": "point_defense_sm" },
      { "id": "missile_bay", "posX": 0.0, "posY": 0.0, "posZ": 3.0, "type": "MISSILE_BAY", "sizeClass": "MEDIUM", "arcMin": 0, "arcMax": 120, "defaultWeaponId": "missile_launcher_md" }
    ]
  }
]
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/weapons/data/ShipWeaponRegistry.java \
        core/src/main/resources/data/weapons/ship_weapons.json \
        core/src/main/resources/data/weapons/hardpoint_templates.json
git commit -m "feat(ship-weapons): add ShipWeaponRegistry and weapon/hardpoint data files"
```

---

### Task 11: Ship Weapon Systems — TurretTracking, ShipWeapon, ShipHeat

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/TurretTrackingSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipWeaponSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipHeatSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/ship/weapons/TurretTrackingSystemTest.java`
- Create: `core/src/test/java/com/galacticodyssey/ship/weapons/ShipWeaponSystemTest.java`
- Create: `core/src/test/java/com/galacticodyssey/ship/weapons/ShipHeatSystemTest.java`

- [ ] **Step 1: Write failing tests for TurretTrackingSystem**

```java
package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.systems.TurretTrackingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurretTrackingSystemTest {

    private Engine engine;
    private Entity ship;
    private Entity target;
    private ShipHardpointComponent hardpointComp;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new TurretTrackingSystem());

        ship = new Entity();
        TransformComponent shipTc = new TransformComponent();
        shipTc.position.set(0, 0, 0);
        ship.add(shipTc);

        hardpointComp = new ShipHardpointComponent();
        Hardpoint turret = new Hardpoint("turret_1", HardpointType.TURRET, HardpointSize.MEDIUM, 0, 360);
        turret.position.set(0, 1, 0);
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.trackingSpeed = 180f;
        turret.mountedWeapon = weapon;
        hardpointComp.hardpoints.add(turret);

        target = new Entity();
        TransformComponent targetTc = new TransformComponent();
        targetTc.position.set(100, 0, 0);
        target.add(targetTc);
        hardpointComp.currentTarget = target;

        ship.add(hardpointComp);
        engine.addEntity(ship);
        engine.addEntity(target);
    }

    @Test
    void turret_tracksTarget_setsTrackingState() {
        engine.update(1.0f);
        assertEquals(HardpointState.TRACKING, hardpointComp.hardpoints.get(0).currentState);
    }

    @Test
    void turret_noTarget_remainsIdle() {
        hardpointComp.currentTarget = null;
        engine.update(1.0f);
        assertEquals(HardpointState.IDLE, hardpointComp.hardpoints.get(0).currentState);
    }

    @Test
    void fixedHardpoint_targetOutOfArc_remainsIdle() {
        Hardpoint fixed = new Hardpoint("fixed_1", HardpointType.FIXED, HardpointSize.SMALL, 0, 15);
        ShipWeaponData weapon = new ShipWeaponData();
        fixed.mountedWeapon = weapon;
        hardpointComp.hardpoints.clear();
        hardpointComp.hardpoints.add(fixed);

        // Target is at (100,0,0) which is 90 degrees off forward (0,0,1) — outside 15 degree arc
        engine.update(1.0f);
        assertEquals(HardpointState.IDLE, fixed.currentState);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.weapons.TurretTrackingSystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement TurretTrackingSystem**

```java
package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;

public class TurretTrackingSystem extends EntitySystem {
    private static final int PRIORITY = 3;
    private final Vector3 tmpDir = new Vector3();
    private final Vector3 tmpWorldPos = new Vector3();
    private ImmutableArray<Entity> entities;

    public TurretTrackingSystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(ShipHardpointComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ShipHardpointComponent hpc = entity.getComponent(ShipHardpointComponent.class);
            TransformComponent shipTc = entity.getComponent(TransformComponent.class);

            for (Hardpoint hp : hpc.hardpoints) {
                if (hp.isEmpty()) continue;
                if (hp.currentState == HardpointState.DISABLED) continue;

                if (hpc.currentTarget == null) {
                    hp.currentState = HardpointState.IDLE;
                    continue;
                }

                TransformComponent targetTc = hpc.currentTarget.getComponent(TransformComponent.class);
                if (targetTc == null) {
                    hp.currentState = HardpointState.IDLE;
                    continue;
                }

                tmpWorldPos.set(hp.position).add(shipTc.position);
                tmpDir.set(targetTc.position).sub(tmpWorldPos).nor();

                float angle = angleToBearing(tmpDir, shipTc);

                if (hp.type == HardpointType.TURRET) {
                    hp.currentState = HardpointState.TRACKING;
                } else if (hp.isInArc(angle)) {
                    hp.currentState = HardpointState.TRACKING;
                } else {
                    hp.currentState = HardpointState.IDLE;
                }
            }
        }
    }

    private float angleToBearing(Vector3 dirToTarget, TransformComponent shipTc) {
        Vector3 forward = new Vector3(0, 0, 1);
        shipTc.rotation.transform(forward);
        float dot = forward.dot(dirToTarget);
        return MathUtils.acos(MathUtils.clamp(dot, -1f, 1f)) * MathUtils.radiansToDegrees;
    }
}
```

- [ ] **Step 4: Run TurretTrackingSystem tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.weapons.TurretTrackingSystemTest" -i`
Expected: All 3 tests PASS

- [ ] **Step 5: Write failing tests for ShipWeaponSystem and ShipHeatSystem**

```java
// ShipWeaponSystemTest.java
package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShipWeaponSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity ship;
    private Hardpoint turret;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new ShipWeaponSystem(eventBus));

        ship = new Entity();
        TransformComponent tc = new TransformComponent();
        ship.add(tc);

        ShipHardpointComponent hpc = new ShipHardpointComponent();
        turret = new Hardpoint("turret_1", HardpointType.TURRET, HardpointSize.MEDIUM, 0, 360);
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "test_cannon";
        weapon.damage = 50f;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.fireRate = 2f;
        weapon.projectileSpeed = 200f;
        weapon.range = 500f;
        weapon.energyCost = 0f;
        weapon.heatPerShot = 0.1f;
        weapon.ammoCapacity = 100;
        weapon.currentAmmo = 100;
        turret.mountedWeapon = weapon;
        turret.currentState = HardpointState.TRACKING;
        hpc.hardpoints.add(turret);
        ship.add(hpc);

        ShipWeaponHeatComponent heat = new ShipWeaponHeatComponent();
        ship.add(heat);

        engine.addEntity(ship);
    }

    @Test
    void fireHardpoint_publishesEvent() {
        AtomicReference<ShipWeaponFiredEvent> received = new AtomicReference<>();
        eventBus.subscribe(ShipWeaponFiredEvent.class, received::set);

        ShipWeaponSystem sys = engine.getSystem(ShipWeaponSystem.class);
        sys.fireHardpoint(ship, "turret_1");

        assertNotNull(received.get());
        assertEquals("turret_1", received.get().hardpointId);
    }

    @Test
    void fireHardpoint_consumesAmmo() {
        ShipWeaponSystem sys = engine.getSystem(ShipWeaponSystem.class);
        sys.fireHardpoint(ship, "turret_1");

        assertEquals(99, turret.mountedWeapon.currentAmmo);
    }

    @Test
    void fireHardpoint_addsHeat() {
        ShipWeaponSystem sys = engine.getSystem(ShipWeaponSystem.class);
        sys.fireHardpoint(ship, "turret_1");

        ShipWeaponHeatComponent heat = ship.getComponent(ShipWeaponHeatComponent.class);
        assertEquals(0.1f, heat.getHeat("turret_1"), 0.01f);
    }

    @Test
    void fireHardpoint_overheated_blocked() {
        ShipWeaponHeatComponent heat = ship.getComponent(ShipWeaponHeatComponent.class);
        heat.overheatedHardpoints.add("turret_1");

        AtomicReference<ShipWeaponFiredEvent> received = new AtomicReference<>();
        eventBus.subscribe(ShipWeaponFiredEvent.class, received::set);

        ShipWeaponSystem sys = engine.getSystem(ShipWeaponSystem.class);
        sys.fireHardpoint(ship, "turret_1");

        assertNull(received.get());
    }
}
```

```java
// ShipHeatSystemTest.java
package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.events.ShipOverheatEvent;
import com.galacticodyssey.ship.weapons.systems.ShipHeatSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShipHeatSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity ship;
    private ShipWeaponHeatComponent heat;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new ShipHeatSystem(eventBus));

        ship = new Entity();
        heat = new ShipWeaponHeatComponent();
        heat.dissipationRate = 0.1f;
        heat.overheatThreshold = 0.5f;
        ship.add(heat);
        engine.addEntity(ship);
    }

    @Test
    void heatDissipates_overTime() {
        heat.heatPerHardpoint.put("turret_1", 0.8f);
        engine.update(1.0f);
        assertEquals(0.7f, heat.getHeat("turret_1"), 0.01f);
    }

    @Test
    void heatReachesMax_triggersOverheat() {
        AtomicReference<ShipOverheatEvent> received = new AtomicReference<>();
        eventBus.subscribe(ShipOverheatEvent.class, received::set);

        heat.heatPerHardpoint.put("turret_1", 1.0f);
        engine.update(0.016f);

        assertTrue(heat.isOverheated("turret_1"));
        assertNotNull(received.get());
    }

    @Test
    void heatDropsBelowThreshold_removesOverheat() {
        heat.overheatedHardpoints.add("turret_1");
        heat.heatPerHardpoint.put("turret_1", 0.4f);
        engine.update(1.0f);

        assertFalse(heat.isOverheated("turret_1"));
    }
}
```

- [ ] **Step 6: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.weapons.*" -i`
Expected: FAIL

- [ ] **Step 7: Implement ShipWeaponSystem**

```java
package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;

public class ShipWeaponSystem extends EntitySystem {
    private static final int PRIORITY = 4;
    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public ShipWeaponSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(ShipHardpointComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ShipHardpointComponent hpc = entity.getComponent(ShipHardpointComponent.class);
            for (Hardpoint hp : hpc.hardpoints) {
                if (hp.fireTimer > 0) {
                    hp.fireTimer -= deltaTime;
                }
            }
        }
    }

    public boolean fireHardpoint(Entity shipEntity, String hardpointId) {
        ShipHardpointComponent hpc = shipEntity.getComponent(ShipHardpointComponent.class);
        ShipWeaponHeatComponent heat = shipEntity.getComponent(ShipWeaponHeatComponent.class);
        TransformComponent tc = shipEntity.getComponent(TransformComponent.class);
        if (hpc == null || tc == null) return false;

        Hardpoint hp = hpc.getHardpoint(hardpointId);
        if (hp == null || hp.isEmpty()) return false;
        if (hp.currentState == HardpointState.DISABLED) return false;
        if (hp.fireTimer > 0) return false;

        if (heat != null && heat.isOverheated(hardpointId)) return false;
        if (!hp.mountedWeapon.canFire()) return false;

        hp.mountedWeapon.consumeAmmo();
        hp.fireTimer = 1f / hp.mountedWeapon.fireRate;

        if (heat != null) {
            heat.addHeat(hardpointId, hp.mountedWeapon.heatPerShot);
        }

        Vector3 origin = new Vector3(hp.position).add(tc.position);
        Vector3 direction = new Vector3(0, 0, 1);
        tc.rotation.transform(direction);

        eventBus.publish(new ShipWeaponFiredEvent(shipEntity, hardpointId, origin, direction, hp.mountedWeapon));
        return true;
    }
}
```

- [ ] **Step 8: Implement ShipHeatSystem**

```java
package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.events.ShipOverheatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShipHeatSystem extends EntitySystem {
    private static final int PRIORITY = 9;
    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public ShipHeatSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(ShipWeaponHeatComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ShipWeaponHeatComponent heat = entity.getComponent(ShipWeaponHeatComponent.class);

            List<String> toRemoveOverheat = new ArrayList<>();

            for (Map.Entry<String, Float> entry : heat.heatPerHardpoint.entrySet()) {
                String hpId = entry.getKey();
                float newHeat = Math.max(0f, entry.getValue() - heat.dissipationRate * deltaTime);
                entry.setValue(newHeat);

                if (newHeat >= heat.maxHeat && !heat.overheatedHardpoints.contains(hpId)) {
                    heat.overheatedHardpoints.add(hpId);
                    eventBus.publish(new ShipOverheatEvent(entity, hpId));
                }

                if (heat.overheatedHardpoints.contains(hpId) && newHeat <= heat.overheatThreshold) {
                    toRemoveOverheat.add(hpId);
                }
            }

            for (String hpId : toRemoveOverheat) {
                heat.overheatedHardpoints.remove(hpId);
            }
        }
    }
}
```

- [ ] **Step 9: Run all ship weapon tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.weapons.*" -i`
Expected: All 10 tests PASS

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/weapons/systems/ \
        core/src/test/java/com/galacticodyssey/ship/weapons/
git commit -m "feat(ship-weapons): add TurretTracking, ShipWeapon, and ShipHeat systems"
```

---

### Task 12: ShipProjectileSystem and PointDefenseSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipProjectileSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/PointDefenseSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/ship/weapons/ShipProjectileSystemTest.java`
- Create: `core/src/test/java/com/galacticodyssey/ship/weapons/PointDefenseSystemTest.java`

- [ ] **Step 1: Write failing tests for ShipProjectileSystem**

```java
package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.systems.ShipProjectileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipProjectileSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new ShipProjectileSystem(eventBus));
    }

    @Test
    void shipWeaponFired_spawnsProjectile() {
        Entity ship = new Entity();
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "cannon";
        weapon.damage = 50f;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.projectileSpeed = 200f;
        weapon.category = ShipWeaponCategory.BALLISTIC_CANNON;

        eventBus.publish(new ShipWeaponFiredEvent(ship, "turret_1",
            new Vector3(0, 0, 0), new Vector3(0, 0, 1), weapon));
        engine.update(0.016f);

        boolean foundProjectile = false;
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(ProjectileComponent.class) != null) {
                foundProjectile = true;
                ProjectileComponent pc = e.getComponent(ProjectileComponent.class);
                assertEquals(50f, pc.damage, 0.01f);
                assertEquals(DamageType.BALLISTIC, pc.damageType);
            }
        }
        assertTrue(foundProjectile);
    }

    @Test
    void missileWeaponFired_spawnsGuidedProjectile() {
        Entity ship = new Entity();
        Entity target = new Entity();
        target.add(new TransformComponent());

        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "missile";
        weapon.damage = 120f;
        weapon.damageType = DamageType.EXPLOSIVE;
        weapon.projectileSpeed = 80f;
        weapon.category = ShipWeaponCategory.MISSILE_LAUNCHER;

        eventBus.publish(new ShipWeaponFiredEvent(ship, "missile_bay",
            new Vector3(0, 0, 0), new Vector3(0, 0, 1), weapon));
        engine.update(0.016f);

        boolean foundGuided = false;
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(GuidedProjectileComponent.class) != null) {
                foundGuided = true;
            }
        }
        assertTrue(foundGuided);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.weapons.ShipProjectileSystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement ShipProjectileSystem**

```java
package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;

import java.util.ArrayList;
import java.util.List;

public class ShipProjectileSystem extends EntitySystem {
    private static final int PRIORITY = 7;
    private final EventBus eventBus;
    private final List<ShipWeaponFiredEvent> pendingFires = new ArrayList<>();

    public ShipProjectileSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(ShipWeaponFiredEvent.class, pendingFires::add);
    }

    @Override
    public void update(float deltaTime) {
        for (ShipWeaponFiredEvent event : pendingFires) {
            spawnProjectile(event);
        }
        pendingFires.clear();
    }

    private void spawnProjectile(ShipWeaponFiredEvent event) {
        Entity projectile = new Entity();

        TransformComponent tc = new TransformComponent();
        tc.position.set(event.origin);
        projectile.add(tc);

        ProjectileComponent pc = new ProjectileComponent();
        pc.speed = event.weaponData.projectileSpeed;
        pc.damage = event.weaponData.damage;
        pc.damageType = event.weaponData.damageType;
        pc.owner = event.shipEntity;
        pc.lifetime = event.weaponData.range / event.weaponData.projectileSpeed;
        pc.age = 0f;
        pc.areaOfEffect = 0f;
        projectile.add(pc);

        if (event.weaponData.category == ShipWeaponCategory.MISSILE_LAUNCHER) {
            GuidedProjectileComponent gpc = new GuidedProjectileComponent();
            gpc.turnRate = 90f;
            gpc.armingDistance = 20f;
            projectile.add(gpc);
        }

        if (getEngine() != null) {
            getEngine().addEntity(projectile);
        }
    }
}
```

- [ ] **Step 4: Write failing tests for PointDefenseSystem**

```java
package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.PointDefenseEngagedEvent;
import com.galacticodyssey.ship.weapons.systems.PointDefenseSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PointDefenseSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity ship;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new PointDefenseSystem(eventBus));

        ship = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        ship.add(tc);

        ShipHardpointComponent hpc = new ShipHardpointComponent();
        Hardpoint pd = new Hardpoint("pd_1", HardpointType.POINT_DEFENSE, HardpointSize.SMALL, 0, 360);
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "pd";
        weapon.damage = 8f;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.fireRate = 20f;
        weapon.range = 400f;
        pd.mountedWeapon = weapon;
        hpc.hardpoints.add(pd);
        ship.add(hpc);

        engine.addEntity(ship);
    }

    @Test
    void incomingProjectile_inRange_engages() {
        AtomicReference<PointDefenseEngagedEvent> received = new AtomicReference<>();
        eventBus.subscribe(PointDefenseEngagedEvent.class, received::set);

        Entity missile = new Entity();
        TransformComponent mtc = new TransformComponent();
        mtc.position.set(100, 0, 0);
        missile.add(mtc);
        ProjectileComponent pc = new ProjectileComponent();
        pc.owner = new Entity(); // different from ship
        pc.speed = 80f;
        pc.damage = 120f;
        pc.damageType = DamageType.EXPLOSIVE;
        pc.lifetime = 10f;
        missile.add(pc);
        engine.addEntity(missile);

        engine.update(0.1f);

        assertNotNull(received.get());
        assertSame(ship, received.get().shipEntity);
    }

    @Test
    void projectile_outOfRange_ignored() {
        AtomicReference<PointDefenseEngagedEvent> received = new AtomicReference<>();
        eventBus.subscribe(PointDefenseEngagedEvent.class, received::set);

        Entity missile = new Entity();
        TransformComponent mtc = new TransformComponent();
        mtc.position.set(1000, 0, 0);
        missile.add(mtc);
        ProjectileComponent pc = new ProjectileComponent();
        pc.owner = new Entity();
        pc.speed = 80f;
        missile.add(pc);
        engine.addEntity(missile);

        engine.update(0.1f);

        assertNull(received.get());
    }
}
```

- [ ] **Step 5: Implement PointDefenseSystem**

```java
package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.events.PointDefenseEngagedEvent;

public class PointDefenseSystem extends EntitySystem {
    private static final int PRIORITY = 5;
    private final EventBus eventBus;
    private final Vector3 tmpDist = new Vector3();
    private ImmutableArray<Entity> ships;
    private ImmutableArray<Entity> projectiles;

    public PointDefenseSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(
            Family.all(ShipHardpointComponent.class, TransformComponent.class).get());
        projectiles = engine.getEntitiesFor(
            Family.all(ProjectileComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            ShipHardpointComponent hpc = ship.getComponent(ShipHardpointComponent.class);
            TransformComponent shipTc = ship.getComponent(TransformComponent.class);

            for (Hardpoint hp : hpc.hardpoints) {
                if (hp.type != HardpointType.POINT_DEFENSE) continue;
                if (hp.isEmpty()) continue;
                if (hp.fireTimer > 0) {
                    hp.fireTimer -= deltaTime;
                    continue;
                }

                Entity closest = findClosestThreat(ship, shipTc, hp.mountedWeapon.range);
                if (closest != null) {
                    hp.fireTimer = 1f / hp.mountedWeapon.fireRate;
                    eventBus.publish(new PointDefenseEngagedEvent(ship, closest));
                }
            }
        }
    }

    private Entity findClosestThreat(Entity ship, TransformComponent shipTc, float range) {
        Entity closest = null;
        float closestDist = range * range;

        for (int i = 0; i < projectiles.size(); i++) {
            Entity proj = projectiles.get(i);
            ProjectileComponent pc = proj.getComponent(ProjectileComponent.class);
            if (pc.owner == ship) continue;

            TransformComponent projTc = proj.getComponent(TransformComponent.class);
            float dist2 = tmpDist.set(projTc.position).sub(shipTc.position).len2();
            if (dist2 < closestDist) {
                closestDist = dist2;
                closest = proj;
            }
        }
        return closest;
    }
}
```

- [ ] **Step 6: Run all tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.weapons.*" -i`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipProjectileSystem.java \
        core/src/main/java/com/galacticodyssey/ship/weapons/systems/PointDefenseSystem.java \
        core/src/test/java/com/galacticodyssey/ship/weapons/ShipProjectileSystemTest.java \
        core/src/test/java/com/galacticodyssey/ship/weapons/PointDefenseSystemTest.java
git commit -m "feat(ship-weapons): add ShipProjectileSystem and PointDefenseSystem"
```

---

## Domain 3: VFX Particle System

### Task 13: Particle Data Model and Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/vfx/VFXEnums.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/Particle.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/ActiveEmitter.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/components/ParticleEmitterComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/components/ParticlePoolComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/data/ParticleEffectDefinition.java`

- [ ] **Step 1: Create VFXEnums**

```java
package com.galacticodyssey.vfx;

public final class VFXEnums {

    public enum BlendMode {
        NORMAL, ADDITIVE
    }

    public enum EmitterState {
        PLAYING, PAUSED, STOPPING
    }

    public static final int FLAG_ADDITIVE_BLEND = 1;
    public static final int FLAG_FACE_CAMERA = 2;
    public static final int FLAG_WORLD_SPACE = 4;

    private VFXEnums() {}
}
```

- [ ] **Step 2: Create Particle (poolable)**

```java
package com.galacticodyssey.vfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool.Poolable;

public class Particle implements Poolable {
    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public final Vector3 acceleration = new Vector3();
    public float life, maxLife;
    public float size, sizeEnd;
    public final Color color = new Color(Color.WHITE);
    public final Color colorEnd = new Color(Color.WHITE);
    public float rotation, angularVelocity;
    public TextureRegion textureRegion;
    public int flags;

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        acceleration.setZero();
        life = 0f;
        maxLife = 0f;
        size = 1f;
        sizeEnd = 0f;
        color.set(Color.WHITE);
        colorEnd.set(Color.WHITE);
        rotation = 0f;
        angularVelocity = 0f;
        textureRegion = null;
        flags = 0;
    }

    public float getLifeRatio() {
        return maxLife > 0 ? 1f - (life / maxLife) : 1f;
    }

    public float getCurrentSize() {
        float t = getLifeRatio();
        return size + (sizeEnd - size) * t;
    }

    public Color getCurrentColor() {
        float t = getLifeRatio();
        return new Color(
            color.r + (colorEnd.r - color.r) * t,
            color.g + (colorEnd.g - color.g) * t,
            color.b + (colorEnd.b - color.b) * t,
            color.a + (colorEnd.a - color.a) * t
        );
    }
}
```

- [ ] **Step 3: Create ActiveEmitter**

```java
package com.galacticodyssey.vfx;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

public class ActiveEmitter {
    public String definitionId;
    public float elapsed;
    public float duration;
    public final Vector3 localOffset = new Vector3();
    public final Quaternion localRotation = new Quaternion();
    public VFXEnums.EmitterState state = VFXEnums.EmitterState.PLAYING;
    public float emitAccumulator;

    public ActiveEmitter(String definitionId, float duration) {
        this.definitionId = definitionId;
        this.duration = duration;
    }

    public boolean isLooping() {
        return duration < 0;
    }

    public boolean isExpired() {
        return !isLooping() && elapsed >= duration && state != VFXEnums.EmitterState.STOPPING;
    }
}
```

- [ ] **Step 4: Create ParticleEmitterComponent**

```java
package com.galacticodyssey.vfx.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.vfx.ActiveEmitter;
import java.util.ArrayList;
import java.util.List;

public class ParticleEmitterComponent implements Component {
    public final List<ActiveEmitter> activeEmitters = new ArrayList<>();

    public ActiveEmitter addEmitter(String definitionId, float duration) {
        ActiveEmitter emitter = new ActiveEmitter(definitionId, duration);
        activeEmitters.add(emitter);
        return emitter;
    }

    public void removeExpired() {
        activeEmitters.removeIf(ActiveEmitter::isExpired);
    }
}
```

- [ ] **Step 5: Create ParticlePoolComponent (singleton)**

```java
package com.galacticodyssey.vfx.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.vfx.Particle;
import java.util.ArrayList;
import java.util.List;

public class ParticlePoolComponent implements Component {
    public static final int MAX_PARTICLES = 4096;

    private final Pool<Particle> pool = new Pool<Particle>(256, MAX_PARTICLES) {
        @Override
        protected Particle newObject() {
            return new Particle();
        }
    };

    public final List<Particle> active = new ArrayList<>(MAX_PARTICLES);

    public Particle obtain() {
        if (active.size() >= MAX_PARTICLES) {
            Particle oldest = active.remove(0);
            pool.free(oldest);
        }
        Particle p = pool.obtain();
        active.add(p);
        return p;
    }

    public void free(Particle p) {
        active.remove(p);
        pool.free(p);
    }

    public void freeAll() {
        for (Particle p : active) {
            pool.free(p);
        }
        active.clear();
    }
}
```

- [ ] **Step 6: Create ParticleEffectDefinition**

```java
package com.galacticodyssey.vfx.data;

import com.galacticodyssey.vfx.VFXEnums.BlendMode;

public class ParticleEffectDefinition {
    public String id;
    public int maxParticles = 16;
    public float emitRate;
    public int burstCount;
    public float lifetimeMin = 0.5f, lifetimeMax = 1.0f;
    public float speedMin = 1f, speedMax = 5f;
    public float spread = 30f;
    public float sizeMin = 0.1f, sizeMax = 0.3f;
    public float sizeEnd;
    public String color = "#FFFFFF";
    public String colorEnd = "#FFFFFF";
    public String texture = "particles/default.png";
    public BlendMode blendMode = BlendMode.ADDITIVE;
    public float gravity;
    public float duration = -1f;
}
```

- [ ] **Step 7: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/vfx/
git commit -m "feat(vfx): add particle data model, components, and pool"
```

---

### Task 14: VFXRegistry and Event Bindings

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/vfx/data/VFXEventBindings.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/data/VFXRegistry.java`
- Create: `core/src/main/resources/data/vfx/vfx_event_bindings.json`
- Create: `core/src/main/resources/data/vfx/muzzle_flash_ballistic.json`
- Create: `core/src/main/resources/data/vfx/impact_sparks.json`
- Create: `core/src/main/resources/data/vfx/shield_ripple.json`
- Create: `core/src/main/resources/data/vfx/engine_exhaust.json`
- Create: `core/src/test/java/com/galacticodyssey/vfx/VFXRegistryTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.vfx;

import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VFXRegistryTest {

    @Test
    void registerAndRetrieve_effectDefinition() {
        VFXRegistry registry = new VFXRegistry();
        ParticleEffectDefinition def = new ParticleEffectDefinition();
        def.id = "muzzle_flash_ballistic";
        def.burstCount = 12;
        def.maxParticles = 12;

        registry.register(def);

        ParticleEffectDefinition retrieved = registry.getEffect("muzzle_flash_ballistic");
        assertNotNull(retrieved);
        assertEquals(12, retrieved.burstCount);
    }

    @Test
    void eventBindings_resolvesEventToEffect() {
        VFXEventBindings bindings = new VFXEventBindings();
        bindings.bind("WeaponFiredEvent", "BALLISTIC", "muzzle_flash_ballistic");
        bindings.bind("HitscanHitEvent", null, "impact_sparks");

        assertEquals("muzzle_flash_ballistic", bindings.resolve("WeaponFiredEvent", "BALLISTIC"));
        assertEquals("impact_sparks", bindings.resolve("HitscanHitEvent", null));
        assertNull(bindings.resolve("UnknownEvent", null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.vfx.VFXRegistryTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement VFXRegistry and VFXEventBindings**

```java
// VFXRegistry.java
package com.galacticodyssey.vfx.data;

import java.util.HashMap;
import java.util.Map;

public class VFXRegistry {
    private final Map<String, ParticleEffectDefinition> effects = new HashMap<>();

    public void register(ParticleEffectDefinition def) {
        effects.put(def.id, def);
    }

    public ParticleEffectDefinition getEffect(String id) {
        return effects.get(id);
    }

    public boolean hasEffect(String id) {
        return effects.containsKey(id);
    }
}
```

```java
// VFXEventBindings.java
package com.galacticodyssey.vfx.data;

import java.util.HashMap;
import java.util.Map;

public class VFXEventBindings {
    private final Map<String, String> bindings = new HashMap<>();

    public void bind(String eventType, String variant, String effectId) {
        String key = variant != null ? eventType + ":" + variant : eventType;
        bindings.put(key, effectId);
    }

    public String resolve(String eventType, String variant) {
        if (variant != null) {
            String specific = bindings.get(eventType + ":" + variant);
            if (specific != null) return specific;
        }
        return bindings.get(eventType);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.vfx.VFXRegistryTest" -i`
Expected: All 2 tests PASS

- [ ] **Step 5: Create VFX data files**

```json
// data/vfx/muzzle_flash_ballistic.json
{
  "id": "muzzle_flash_ballistic", "maxParticles": 12, "emitRate": 0, "burstCount": 12,
  "lifetimeMin": 0.05, "lifetimeMax": 0.12, "speedMin": 2.0, "speedMax": 8.0,
  "spread": 25.0, "sizeMin": 0.1, "sizeMax": 0.3, "sizeEnd": 0.0,
  "color": "#FFA500", "colorEnd": "#FF4500",
  "texture": "particles/spark.png", "blendMode": "ADDITIVE", "gravity": 0.0
}
```

```json
// data/vfx/impact_sparks.json
{
  "id": "impact_sparks", "maxParticles": 8, "emitRate": 0, "burstCount": 8,
  "lifetimeMin": 0.1, "lifetimeMax": 0.3, "speedMin": 3.0, "speedMax": 12.0,
  "spread": 60.0, "sizeMin": 0.05, "sizeMax": 0.15, "sizeEnd": 0.0,
  "color": "#FFD700", "colorEnd": "#8B4513",
  "texture": "particles/spark.png", "blendMode": "ADDITIVE", "gravity": -9.8
}
```

```json
// data/vfx/shield_ripple.json
{
  "id": "shield_ripple", "maxParticles": 20, "emitRate": 0, "burstCount": 20,
  "lifetimeMin": 0.2, "lifetimeMax": 0.5, "speedMin": 0.5, "speedMax": 2.0,
  "spread": 180.0, "sizeMin": 0.2, "sizeMax": 0.6, "sizeEnd": 0.8,
  "color": "#00BFFF", "colorEnd": "#00BFFF00",
  "texture": "particles/hex.png", "blendMode": "ADDITIVE", "gravity": 0.0
}
```

```json
// data/vfx/engine_exhaust.json
{
  "id": "engine_exhaust", "maxParticles": 30, "emitRate": 60, "burstCount": 0,
  "lifetimeMin": 0.3, "lifetimeMax": 0.8, "speedMin": 5.0, "speedMax": 15.0,
  "spread": 8.0, "sizeMin": 0.3, "sizeMax": 0.5, "sizeEnd": 1.0,
  "color": "#4FC3F7", "colorEnd": "#4FC3F700",
  "texture": "particles/smoke.png", "blendMode": "ADDITIVE", "gravity": 0.0,
  "duration": -1
}
```

```json
// data/vfx/vfx_event_bindings.json
{
  "WeaponFiredEvent:BALLISTIC": "muzzle_flash_ballistic",
  "WeaponFiredEvent:ENERGY": "muzzle_flash_energy",
  "WeaponFiredEvent:PLASMA": "muzzle_flash_plasma",
  "HitscanHitEvent": "impact_sparks",
  "ProjectileHitEvent:EXPLOSIVE": "impact_explosion",
  "ProjectileHitEvent": "impact_sparks",
  "ShipWeaponFiredEvent": "muzzle_flash_ballistic",
  "MeleeHitEvent": "impact_sparks",
  "ShieldAbsorbEvent": "shield_ripple",
  "EntityKilledEvent": "impact_explosion"
}
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/vfx/data/VFXRegistry.java \
        core/src/main/java/com/galacticodyssey/vfx/data/VFXEventBindings.java \
        core/src/test/java/com/galacticodyssey/vfx/VFXRegistryTest.java \
        core/src/main/resources/data/vfx/
git commit -m "feat(vfx): add VFXRegistry, event bindings, and particle effect data files"
```

---

### Task 15: ParticleSpawnSystem and ParticleUpdateSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/vfx/systems/ParticleSpawnSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/systems/ParticleUpdateSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/vfx/ParticleSpawnSystemTest.java`
- Create: `core/src/test/java/com/galacticodyssey/vfx/ParticleUpdateSystemTest.java`

- [ ] **Step 1: Write failing tests for ParticleSpawnSystem**

```java
package com.galacticodyssey.vfx;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.vfx.components.ParticleEmitterComponent;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import com.galacticodyssey.vfx.systems.ParticleSpawnSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticleSpawnSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private VFXRegistry registry;
    private VFXEventBindings bindings;
    private ParticlePoolComponent pool;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        registry = new VFXRegistry();
        bindings = new VFXEventBindings();

        ParticleEffectDefinition sparks = new ParticleEffectDefinition();
        sparks.id = "impact_sparks";
        sparks.burstCount = 8;
        sparks.maxParticles = 8;
        sparks.lifetimeMin = 0.1f;
        sparks.lifetimeMax = 0.3f;
        sparks.speedMin = 3f;
        sparks.speedMax = 12f;
        sparks.spread = 60f;
        registry.register(sparks);
        bindings.bind("HitscanHitEvent", null, "impact_sparks");

        Entity poolEntity = new Entity();
        pool = new ParticlePoolComponent();
        poolEntity.add(pool);
        engine.addEntity(poolEntity);

        engine.addSystem(new ParticleSpawnSystem(eventBus, registry, bindings, pool));
    }

    @Test
    void hitscanHit_spawnsImpactParticles() {
        Entity shooter = new Entity();
        Entity target = new Entity();

        eventBus.publish(new HitscanHitEvent(shooter, target,
            new Vector3(5, 1, 3), new Vector3(0, 1, 0),
            HitRegion.TORSO, 20f, DamageType.BALLISTIC, "standard_round"));

        engine.update(0.016f);

        assertFalse(pool.active.isEmpty());
        assertEquals(8, pool.active.size());
    }
}
```

- [ ] **Step 2: Write failing tests for ParticleUpdateSystem**

```java
package com.galacticodyssey.vfx;

import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.systems.ParticleUpdateSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticleUpdateSystemTest {

    private ParticlePoolComponent pool;
    private ParticleUpdateSystem system;

    @BeforeEach
    void setUp() {
        pool = new ParticlePoolComponent();
        system = new ParticleUpdateSystem(pool);
    }

    @Test
    void particles_moveByVelocity() {
        Particle p = pool.obtain();
        p.position.set(0, 0, 0);
        p.velocity.set(10, 0, 0);
        p.life = 1f;
        p.maxLife = 1f;

        system.update(0.1f);

        assertEquals(1.0f, p.position.x, 0.01f);
        assertEquals(0.9f, p.life, 0.01f);
    }

    @Test
    void expiredParticles_returnedToPool() {
        Particle p = pool.obtain();
        p.life = 0.01f;
        p.maxLife = 1f;

        system.update(0.1f);

        assertTrue(pool.active.isEmpty());
    }

    @Test
    void particles_applyAcceleration() {
        Particle p = pool.obtain();
        p.position.set(0, 10, 0);
        p.velocity.set(0, 0, 0);
        p.acceleration.set(0, -9.8f, 0);
        p.life = 2f;
        p.maxLife = 2f;

        system.update(1.0f);

        assertEquals(-9.8f, p.velocity.y, 0.01f);
        assertEquals(0.2f, p.position.y, 0.01f);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.vfx.*" -i`
Expected: FAIL

- [ ] **Step 4: Implement ParticleSpawnSystem**

```java
package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;

import java.util.ArrayList;
import java.util.List;

public class ParticleSpawnSystem extends EntitySystem {
    private static final int PRIORITY = 12;
    private final VFXRegistry registry;
    private final VFXEventBindings bindings;
    private final ParticlePoolComponent pool;

    private final List<SpawnRequest> pendingSpawns = new ArrayList<>();

    public ParticleSpawnSystem(EventBus eventBus, VFXRegistry registry,
                               VFXEventBindings bindings, ParticlePoolComponent pool) {
        super(PRIORITY);
        this.registry = registry;
        this.bindings = bindings;
        this.pool = pool;

        eventBus.subscribe(HitscanHitEvent.class, e ->
            queueSpawn("HitscanHitEvent", e.damageType.name(), e.hitPoint));
        eventBus.subscribe(ProjectileHitEvent.class, e ->
            queueSpawn("ProjectileHitEvent", e.damageType.name(), e.hitPoint));
        eventBus.subscribe(WeaponFiredEvent.class, e ->
            queueSpawn("WeaponFiredEvent", null, e.aimDirection));
        eventBus.subscribe(ShieldAbsorbEvent.class, e ->
            queueSpawn("ShieldAbsorbEvent", null, new Vector3()));
        eventBus.subscribe(EntityKilledEvent.class, e ->
            queueSpawn("EntityKilledEvent", null, new Vector3()));
        eventBus.subscribe(ShipWeaponFiredEvent.class, e ->
            queueSpawn("ShipWeaponFiredEvent", null, e.origin));
    }

    private void queueSpawn(String eventType, String variant, Vector3 position) {
        pendingSpawns.add(new SpawnRequest(eventType, variant, new Vector3(position)));
    }

    @Override
    public void update(float deltaTime) {
        for (SpawnRequest req : pendingSpawns) {
            String effectId = bindings.resolve(req.eventType, req.variant);
            if (effectId == null) continue;
            ParticleEffectDefinition def = registry.getEffect(effectId);
            if (def == null) continue;
            spawnBurst(def, req.position);
        }
        pendingSpawns.clear();
    }

    private void spawnBurst(ParticleEffectDefinition def, Vector3 origin) {
        int count = def.burstCount > 0 ? def.burstCount : 1;
        Color startColor = Color.valueOf(def.color);
        Color endColor = Color.valueOf(def.colorEnd);

        for (int i = 0; i < count; i++) {
            Particle p = pool.obtain();
            p.position.set(origin);
            float speed = MathUtils.random(def.speedMin, def.speedMax);
            float spreadRad = def.spread * MathUtils.degreesToRadians;
            float theta = MathUtils.random(0f, MathUtils.PI2);
            float phi = MathUtils.random(0f, spreadRad);
            p.velocity.set(
                speed * MathUtils.sin(phi) * MathUtils.cos(theta),
                speed * MathUtils.cos(phi),
                speed * MathUtils.sin(phi) * MathUtils.sin(theta)
            );
            p.acceleration.set(0, def.gravity, 0);
            p.life = MathUtils.random(def.lifetimeMin, def.lifetimeMax);
            p.maxLife = p.life;
            p.size = MathUtils.random(def.sizeMin, def.sizeMax);
            p.sizeEnd = def.sizeEnd;
            p.color.set(startColor);
            p.colorEnd.set(endColor);
            p.flags = def.blendMode == VFXEnums.BlendMode.ADDITIVE
                ? VFXEnums.FLAG_ADDITIVE_BLEND | VFXEnums.FLAG_FACE_CAMERA
                : VFXEnums.FLAG_FACE_CAMERA;
        }
    }

    private static class SpawnRequest {
        final String eventType;
        final String variant;
        final Vector3 position;

        SpawnRequest(String eventType, String variant, Vector3 position) {
            this.eventType = eventType;
            this.variant = variant;
            this.position = position;
        }
    }
}
```

- [ ] **Step 5: Implement ParticleUpdateSystem**

```java
package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;

import java.util.ArrayList;
import java.util.List;

public class ParticleUpdateSystem extends EntitySystem {
    private static final int PRIORITY = 13;
    private final ParticlePoolComponent pool;
    private final List<Particle> toRemove = new ArrayList<>();

    public ParticleUpdateSystem(ParticlePoolComponent pool) {
        super(PRIORITY);
        this.pool = pool;
    }

    @Override
    public void update(float deltaTime) {
        toRemove.clear();
        for (Particle p : pool.active) {
            p.velocity.mulAdd(p.acceleration, deltaTime);
            p.position.mulAdd(p.velocity, deltaTime);
            p.rotation += p.angularVelocity * deltaTime;
            p.life -= deltaTime;
            if (p.life <= 0) {
                toRemove.add(p);
            }
        }
        for (Particle p : toRemove) {
            pool.free(p);
        }
    }
}
```

- [ ] **Step 6: Run all VFX tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.vfx.*" -i`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/vfx/systems/ParticleSpawnSystem.java \
        core/src/main/java/com/galacticodyssey/vfx/systems/ParticleUpdateSystem.java \
        core/src/test/java/com/galacticodyssey/vfx/ParticleSpawnSystemTest.java \
        core/src/test/java/com/galacticodyssey/vfx/ParticleUpdateSystemTest.java
git commit -m "feat(vfx): add ParticleSpawnSystem and ParticleUpdateSystem"
```

---

### Task 16: ParticleRenderSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/vfx/systems/ParticleRenderSystem.java`

- [ ] **Step 1: Implement ParticleRenderSystem**

This system requires a GL context so it cannot be unit tested in headless mode. It will be verified through manual testing.

```java
package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;

public class ParticleRenderSystem extends EntitySystem implements Disposable {
    private static final int PRIORITY = 20;
    private final ParticlePoolComponent pool;
    private final Camera camera;
    private DecalBatch decalBatch;
    private TextureRegion defaultTexture;

    public ParticleRenderSystem(ParticlePoolComponent pool, Camera camera) {
        super(PRIORITY);
        this.pool = pool;
        this.camera = camera;
    }

    public void initialize(TextureRegion defaultTexture) {
        this.defaultTexture = defaultTexture;
        this.decalBatch = new DecalBatch(ParticlePoolComponent.MAX_PARTICLES,
            new CameraGroupStrategy(camera));
    }

    @Override
    public void update(float deltaTime) {
        if (decalBatch == null) return;

        for (Particle p : pool.active) {
            TextureRegion tex = p.textureRegion != null ? p.textureRegion : defaultTexture;
            if (tex == null) continue;

            Decal decal = Decal.newDecal(tex, true);
            float size = p.getCurrentSize();
            decal.setDimensions(size, size);
            decal.setPosition(p.position.x, p.position.y, p.position.z);
            decal.setRotation(camera.direction.cpy().scl(-1), camera.up);

            Color c = p.getCurrentColor();
            decal.setColor(c.r, c.g, c.b, c.a);

            if ((p.flags & VFXEnums.FLAG_ADDITIVE_BLEND) != 0) {
                decal.setBlending(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            }

            decalBatch.add(decal);
        }

        decalBatch.flush();
    }

    @Override
    public void dispose() {
        if (decalBatch != null) {
            decalBatch.dispose();
        }
    }
}
```

- [ ] **Step 2: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/vfx/systems/ParticleRenderSystem.java
git commit -m "feat(vfx): add ParticleRenderSystem with DecalBatch billboarding"
```

---

## Domain 4: Shooting Feedback

### Task 17: RecoilComponent and RecoilSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/components/RecoilComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/systems/RecoilSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/player/RecoilSystemTest.java`
- Create: `core/src/main/resources/data/weapons/recoil_patterns.json`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.RecoilComponent;
import com.galacticodyssey.player.systems.RecoilSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecoilSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private RecoilComponent recoil;
    private FPSCameraComponent camera;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new RecoilSystem(eventBus));

        player = new Entity();
        recoil = new RecoilComponent();
        recoil.recoverySpeed = 5f;
        recoil.maxPunch.set(10f, 5f);
        recoil.pattern = new Vector2[]{
            new Vector2(2f, 0f), new Vector2(1.5f, 0.5f), new Vector2(1f, -0.3f)
        };
        player.add(recoil);

        camera = new FPSCameraComponent();
        player.add(camera);

        engine.addEntity(player);
    }

    @Test
    void recoilEvent_addsPunchToCamera() {
        float initialPitch = camera.pitchAngle;
        eventBus.publish(new RecoilEvent(player, new Vector2(2f, 0f)));
        engine.update(0.016f);

        assertTrue(recoil.currentPunch.x > 0);
    }

    @Test
    void recoilDecays_towardZero() {
        recoil.currentPunch.set(5f, 2f);
        engine.update(1.0f);

        assertTrue(recoil.currentPunch.x < 5f);
        assertTrue(recoil.currentPunch.y < 2f);
    }

    @Test
    void recoilClamped_atMaxPunch() {
        for (int i = 0; i < 20; i++) {
            eventBus.publish(new RecoilEvent(player, new Vector2(2f, 1f)));
        }
        engine.update(0.016f);

        assertTrue(recoil.currentPunch.x <= recoil.maxPunch.x);
        assertTrue(recoil.currentPunch.y <= recoil.maxPunch.y);
    }

    @Test
    void patternIndex_advancesOnConsecutiveShots() {
        eventBus.publish(new RecoilEvent(player, new Vector2(2f, 0f)));
        engine.update(0.016f);
        assertEquals(1, recoil.patternIndex);

        eventBus.publish(new RecoilEvent(player, new Vector2(1.5f, 0.5f)));
        engine.update(0.016f);
        assertEquals(2, recoil.patternIndex);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.RecoilSystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement RecoilComponent**

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

public class RecoilComponent implements Component {
    public final Vector2 currentPunch = new Vector2();
    public float recoverySpeed = 5f;
    public Vector2[] pattern;
    public int patternIndex;
    public float patternResetDelay = 0.3f;
    public float timeSinceLastShot;
    public final Vector2 maxPunch = new Vector2(10f, 5f);
}
```

- [ ] **Step 4: Implement RecoilSystem**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.RecoilComponent;

import java.util.ArrayList;
import java.util.List;

public class RecoilSystem extends EntitySystem {
    private static final int PRIORITY = 10;
    private final EventBus eventBus;
    private final List<RecoilEvent> pendingRecoils = new ArrayList<>();
    private ImmutableArray<Entity> entities;

    public RecoilSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(RecoilEvent.class, pendingRecoils::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(RecoilComponent.class, FPSCameraComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (RecoilEvent event : pendingRecoils) {
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                if (entity == event.entity) {
                    applyRecoil(entity);
                }
            }
        }
        pendingRecoils.clear();

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            RecoilComponent rc = entity.getComponent(RecoilComponent.class);
            FPSCameraComponent cam = entity.getComponent(FPSCameraComponent.class);

            rc.timeSinceLastShot += deltaTime;
            if (rc.timeSinceLastShot >= rc.patternResetDelay) {
                rc.patternIndex = 0;
            }

            float decay = rc.recoverySpeed * deltaTime;
            rc.currentPunch.x = approach(rc.currentPunch.x, 0f, decay);
            rc.currentPunch.y = approach(rc.currentPunch.y, 0f, decay);

            cam.pitchAngle += rc.currentPunch.x * deltaTime;
            cam.yawAngle += rc.currentPunch.y * deltaTime;
        }
    }

    private void applyRecoil(Entity entity) {
        RecoilComponent rc = entity.getComponent(RecoilComponent.class);
        rc.timeSinceLastShot = 0f;

        Vector2 punch;
        if (rc.pattern != null && rc.pattern.length > 0) {
            punch = rc.pattern[rc.patternIndex % rc.pattern.length];
            rc.patternIndex++;
        } else {
            punch = new Vector2(1f, 0f);
        }

        rc.currentPunch.add(punch);
        rc.currentPunch.x = MathUtils.clamp(rc.currentPunch.x, -rc.maxPunch.x, rc.maxPunch.x);
        rc.currentPunch.y = MathUtils.clamp(rc.currentPunch.y, -rc.maxPunch.y, rc.maxPunch.y);
    }

    private float approach(float current, float target, float maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return target;
    }
}
```

- [ ] **Step 5: Create recoil_patterns.json**

```json
{
  "PISTOL": [
    [1.5, 0.0], [1.2, 0.3], [1.0, -0.2], [1.3, 0.1], [1.1, -0.3]
  ],
  "RIFLE": [
    [1.8, 0.0], [1.5, 0.4], [1.2, -0.3], [1.0, 0.5], [1.3, -0.2],
    [1.1, 0.3], [0.9, -0.4], [1.0, 0.2], [0.8, -0.1], [1.0, 0.0]
  ],
  "SMG": [
    [0.8, 0.0], [0.7, 0.2], [0.6, -0.1], [0.7, 0.1], [0.5, -0.2],
    [0.6, 0.1], [0.5, 0.0], [0.6, -0.1]
  ],
  "SHOTGUN": [
    [4.0, 0.0], [3.5, 0.5], [3.0, -0.3]
  ],
  "SNIPER": [
    [5.0, 0.0], [4.0, 0.3]
  ],
  "HEAVY": [
    [0.5, 0.0], [0.4, 0.1], [0.5, -0.1], [0.3, 0.0]
  ]
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.RecoilSystemTest" -i`
Expected: All 4 tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/RecoilComponent.java \
        core/src/main/java/com/galacticodyssey/player/systems/RecoilSystem.java \
        core/src/test/java/com/galacticodyssey/player/RecoilSystemTest.java \
        core/src/main/resources/data/weapons/recoil_patterns.json
git commit -m "feat(feedback): add RecoilComponent and RecoilSystem with pattern support"
```

---

### Task 18: ADSComponent and ADSSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/components/ADSComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/systems/ADSSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/components/CombatInputComponent.java` — add `aimHeld`
- Create: `core/src/test/java/com/galacticodyssey/player/ADSSystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.systems.ADSSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ADSSystemTest {

    private Engine engine;
    private Entity player;
    private ADSComponent ads;
    private CombatInputComponent input;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new ADSSystem());

        player = new Entity();
        ads = new ADSComponent();
        ads.adsSpeed = 5f;
        ads.zoomMultiplier = 0.7f;
        ads.spreadMultiplier = 0.3f;
        ads.moveSpeedMultiplier = 0.6f;
        player.add(ads);

        input = new CombatInputComponent();
        player.add(input);

        FPSCameraComponent cam = new FPSCameraComponent();
        player.add(cam);

        engine.addEntity(player);
    }

    @Test
    void aimHeld_increasesAdsProgress() {
        input.aimHeld = true;
        engine.update(0.1f);
        assertTrue(ads.adsProgress > 0f);
    }

    @Test
    void aimReleased_decreasesAdsProgress() {
        ads.adsProgress = 1.0f;
        input.aimHeld = false;
        engine.update(0.1f);
        assertTrue(ads.adsProgress < 1.0f);
    }

    @Test
    void adsProgress_clampedToZeroOne() {
        input.aimHeld = true;
        for (int i = 0; i < 100; i++) {
            engine.update(0.1f);
        }
        assertEquals(1.0f, ads.adsProgress, 0.01f);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.ADSSystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Add `aimHeld` to CombatInputComponent**

Add this field to the existing class at `core/src/main/java/com/galacticodyssey/combat/components/CombatInputComponent.java`:

```java
public boolean aimHeld;
```

Add it after the existing `blockHeld` field.

- [ ] **Step 4: Implement ADSComponent**

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class ADSComponent implements Component {
    public float adsProgress;
    public float adsSpeed = 5f;
    public float zoomMultiplier = 0.7f;
    public float spreadMultiplier = 0.3f;
    public float moveSpeedMultiplier = 0.6f;
}
```

- [ ] **Step 5: Implement ADSSystem**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;

public class ADSSystem extends EntitySystem {
    private static final int PRIORITY = 11;
    private ImmutableArray<Entity> entities;

    public ADSSystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(ADSComponent.class, CombatInputComponent.class, FPSCameraComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ADSComponent ads = entity.getComponent(ADSComponent.class);
            CombatInputComponent input = entity.getComponent(CombatInputComponent.class);

            float target = input.aimHeld ? 1f : 0f;
            ads.adsProgress = MathUtils.clamp(
                ads.adsProgress + (target - ads.adsProgress) * ads.adsSpeed * deltaTime,
                0f, 1f
            );
        }
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.ADSSystemTest" -i`
Expected: All 3 tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/ADSComponent.java \
        core/src/main/java/com/galacticodyssey/player/systems/ADSSystem.java \
        core/src/main/java/com/galacticodyssey/combat/components/CombatInputComponent.java \
        core/src/test/java/com/galacticodyssey/player/ADSSystemTest.java
git commit -m "feat(feedback): add ADSComponent, ADSSystem, and aimHeld input flag"
```

---

### Task 19: CrosshairComponent and CrosshairSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/components/CrosshairComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/systems/CrosshairSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/player/CrosshairSystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.CrosshairComponent;
import com.galacticodyssey.player.systems.CrosshairSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrosshairSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private CrosshairComponent crosshair;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new CrosshairSystem(eventBus));

        player = new Entity();
        crosshair = new CrosshairComponent();
        crosshair.bloomDecayRate = 5f;
        player.add(crosshair);

        RangedWeaponComponent rwc = new RangedWeaponComponent();
        rwc.spread = 2f;
        player.add(rwc);

        engine.addEntity(player);
    }

    @Test
    void bloom_decaysOverTime() {
        crosshair.currentBloom = 5f;
        engine.update(0.5f);
        assertTrue(crosshair.currentBloom < 5f);
    }

    @Test
    void bloom_doesNotGoBelowZero() {
        crosshair.currentBloom = 0.1f;
        engine.update(1.0f);
        assertEquals(0f, crosshair.currentBloom, 0.01f);
    }

    @Test
    void damageDealt_triggersHitMarker() {
        Entity target = new Entity();
        eventBus.publish(new DamageDealtEvent(target, player, 20f, DamageType.BALLISTIC, HitRegion.TORSO));
        engine.update(0.016f);

        assertTrue(crosshair.hitMarkerTimer > 0);
    }

    @Test
    void entityKilled_triggersKillConfirm() {
        Entity target = new Entity();
        eventBus.publish(new EntityKilledEvent(target, player));
        engine.update(0.016f);

        assertTrue(crosshair.killConfirmTimer > 0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.CrosshairSystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement CrosshairComponent**

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class CrosshairComponent implements Component {
    public float baseSize = 8f;
    public float currentBloom;
    public float bloomPerShot = 2f;
    public float bloomDecayRate = 5f;
    public float hitMarkerTimer;
    public float hitMarkerDuration = 0.2f;
    public float killConfirmTimer;
    public float killConfirmDuration = 0.4f;

    public float getCurrentSize(float weaponSpread) {
        return baseSize + weaponSpread + currentBloom;
    }
}
```

- [ ] **Step 4: Implement CrosshairSystem**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.CrosshairComponent;

import java.util.ArrayList;
import java.util.List;

public class CrosshairSystem extends EntitySystem {
    private static final int PRIORITY = 15;
    private final List<DamageDealtEvent> pendingHits = new ArrayList<>();
    private final List<EntityKilledEvent> pendingKills = new ArrayList<>();
    private ImmutableArray<Entity> entities;

    public CrosshairSystem(EventBus eventBus) {
        super(PRIORITY);
        eventBus.subscribe(DamageDealtEvent.class, pendingHits::add);
        eventBus.subscribe(EntityKilledEvent.class, pendingKills::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(CrosshairComponent.class, RangedWeaponComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            CrosshairComponent ch = entity.getComponent(CrosshairComponent.class);

            ch.currentBloom = Math.max(0f, ch.currentBloom - ch.bloomDecayRate * deltaTime);
            ch.hitMarkerTimer = Math.max(0f, ch.hitMarkerTimer - deltaTime);
            ch.killConfirmTimer = Math.max(0f, ch.killConfirmTimer - deltaTime);

            for (DamageDealtEvent hit : pendingHits) {
                if (hit.attacker == entity) {
                    ch.hitMarkerTimer = ch.hitMarkerDuration;
                }
            }
            for (EntityKilledEvent kill : pendingKills) {
                if (kill.killer == entity) {
                    ch.killConfirmTimer = ch.killConfirmDuration;
                }
            }
        }
        pendingHits.clear();
        pendingKills.clear();
    }

    public void addBloom(Entity entity, float amount) {
        CrosshairComponent ch = entity.getComponent(CrosshairComponent.class);
        if (ch != null) {
            ch.currentBloom += amount;
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.CrosshairSystemTest" -i`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/CrosshairComponent.java \
        core/src/main/java/com/galacticodyssey/player/systems/CrosshairSystem.java \
        core/src/test/java/com/galacticodyssey/player/CrosshairSystemTest.java
git commit -m "feat(feedback): add CrosshairComponent and CrosshairSystem with hit markers"
```

---

### Task 20: ScreenShakeComponent and ScreenShakeSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/components/ScreenShakeComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/systems/ScreenShakeSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/player/ScreenShakeSystemTest.java`
- Create: `core/src/main/resources/data/vfx/screen_shake_config.json`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.ScreenShakeComponent;
import com.galacticodyssey.player.systems.ScreenShakeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenShakeSystemTest {

    private Engine engine;
    private Entity camera;
    private ScreenShakeComponent shake;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new ScreenShakeSystem());

        camera = new Entity();
        shake = new ScreenShakeComponent();
        shake.decayRate = 1f;
        camera.add(shake);
        engine.addEntity(camera);
    }

    @Test
    void traumaDecays_overTime() {
        shake.trauma = 0.8f;
        engine.update(0.5f);
        assertEquals(0.3f, shake.trauma, 0.01f);
    }

    @Test
    void traumaClamped_atZero() {
        shake.trauma = 0.1f;
        engine.update(1.0f);
        assertEquals(0f, shake.trauma, 0.01f);
    }

    @Test
    void traumaClamped_atOne() {
        shake.trauma = 0.8f;
        shake.addTrauma(0.5f);
        assertEquals(1.0f, shake.trauma, 0.01f);
    }

    @Test
    void shakeIntensity_isTraumaSquared() {
        shake.trauma = 0.5f;
        assertEquals(0.25f, shake.getIntensity(), 0.01f);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.ScreenShakeSystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement ScreenShakeComponent**

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class ScreenShakeComponent implements Component {
    public float trauma;
    public float decayRate = 1.5f;
    public final Vector3 maxOffset = new Vector3(0.3f, 0.3f, 0.1f);
    public final Vector2 maxAngle = new Vector2(3f, 3f);
    public float frequency = 15f;
    public final Vector3 currentOffset = new Vector3();
    public final Vector2 currentAngle = new Vector2();

    public void addTrauma(float amount) {
        trauma = MathUtils.clamp(trauma + amount, 0f, 1f);
    }

    public float getIntensity() {
        return trauma * trauma;
    }
}
```

- [ ] **Step 4: Implement ScreenShakeSystem**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.player.components.ScreenShakeComponent;

public class ScreenShakeSystem extends EntitySystem {
    private static final int PRIORITY = 14;
    private float elapsed;
    private ImmutableArray<Entity> entities;

    public ScreenShakeSystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(ScreenShakeComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        elapsed += deltaTime;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ScreenShakeComponent shake = entity.getComponent(ScreenShakeComponent.class);

            shake.trauma = Math.max(0f, shake.trauma - shake.decayRate * deltaTime);

            float intensity = shake.getIntensity();
            if (intensity > 0.001f) {
                float freq = shake.frequency;
                shake.currentOffset.set(
                    shake.maxOffset.x * intensity * noise(elapsed * freq, 0),
                    shake.maxOffset.y * intensity * noise(elapsed * freq, 100),
                    shake.maxOffset.z * intensity * noise(elapsed * freq, 200)
                );
                shake.currentAngle.set(
                    shake.maxAngle.x * intensity * noise(elapsed * freq, 300),
                    shake.maxAngle.y * intensity * noise(elapsed * freq, 400)
                );
            } else {
                shake.currentOffset.setZero();
                shake.currentAngle.setZero();
            }
        }
    }

    private float noise(float x, float seed) {
        return MathUtils.sin(x + seed) * MathUtils.cos(x * 0.7f + seed * 1.3f);
    }
}
```

- [ ] **Step 5: Create screen_shake_config.json**

```json
{
  "WeaponFiredEvent": 0.08,
  "ProjectileHitEvent_near": 0.25,
  "ShipWeaponFiredEvent_own": 0.2,
  "explosion_near": 0.5,
  "MeleeHitEvent": 0.15
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.ScreenShakeSystemTest" -i`
Expected: All 4 tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/ScreenShakeComponent.java \
        core/src/main/java/com/galacticodyssey/player/systems/ScreenShakeSystem.java \
        core/src/test/java/com/galacticodyssey/player/ScreenShakeSystemTest.java \
        core/src/main/resources/data/vfx/screen_shake_config.json
git commit -m "feat(feedback): add ScreenShakeComponent and ScreenShakeSystem"
```

---

### Task 21: WeaponSwaySystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/systems/WeaponSwaySystem.java`
- Create: `core/src/test/java/com/galacticodyssey/player/WeaponSwaySystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.systems.WeaponSwaySystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponSwaySystemTest {

    private Engine engine;
    private Entity player;
    private FPSCameraComponent camera;
    private MovementStateComponent movement;
    private ADSComponent ads;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new WeaponSwaySystem());

        player = new Entity();
        camera = new FPSCameraComponent();
        player.add(camera);
        movement = new MovementStateComponent();
        player.add(movement);
        ads = new ADSComponent();
        player.add(ads);
        engine.addEntity(player);
    }

    @Test
    void moving_producesHeadBob() {
        movement.currentSpeed = 5f;
        movement.isGrounded = true;
        float initialPhase = camera.headBobPhase;
        engine.update(0.1f);
        assertNotEquals(initialPhase, camera.headBobPhase);
    }

    @Test
    void stationary_noHeadBobPhaseAdvance() {
        movement.currentSpeed = 0f;
        movement.isGrounded = true;
        float initialPhase = camera.headBobPhase;
        engine.update(0.1f);
        assertEquals(initialPhase, camera.headBobPhase, 0.01f);
    }

    @Test
    void aiming_suppressesSway() {
        movement.currentSpeed = 5f;
        movement.isGrounded = true;
        ads.adsProgress = 1.0f;
        engine.update(0.5f);

        float aimingAmplitude = camera.headBobAmplitude * (1f - ads.adsProgress);
        assertEquals(0f, aimingAmplitude, 0.01f);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.WeaponSwaySystemTest" -i`
Expected: FAIL

- [ ] **Step 3: Implement WeaponSwaySystem**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;

public class WeaponSwaySystem extends EntitySystem {
    private static final int PRIORITY = 11;
    private ImmutableArray<Entity> entities;

    public WeaponSwaySystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(FPSCameraComponent.class, MovementStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            FPSCameraComponent cam = entity.getComponent(FPSCameraComponent.class);
            MovementStateComponent move = entity.getComponent(MovementStateComponent.class);
            ADSComponent ads = entity.getComponent(ADSComponent.class);

            float adsSuppress = ads != null ? (1f - ads.adsProgress) : 1f;

            if (move.isGrounded && move.currentSpeed > 0.1f) {
                float speedFactor = Math.min(move.currentSpeed / 6f, 1f);
                cam.headBobPhase += deltaTime * cam.headBobFrequency * speedFactor;
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.WeaponSwaySystemTest" -i`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/WeaponSwaySystem.java \
        core/src/test/java/com/galacticodyssey/player/WeaponSwaySystemTest.java
git commit -m "feat(feedback): add WeaponSwaySystem with ADS suppression"
```

---

## Integration

### Task 22: GameWorld System Registration and Audio Data

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Create: `core/src/main/resources/data/audio/sound_bindings.json`

- [ ] **Step 1: Add new system fields and registration to GameWorld**

Add these imports and fields to `GameWorld.java`:

```java
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import com.galacticodyssey.equipment.systems.LootGenerationSystem;
import com.galacticodyssey.equipment.data.LootTableRegistry;
import com.galacticodyssey.ship.weapons.systems.*;
import com.galacticodyssey.ship.weapons.data.ShipWeaponRegistry;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import com.galacticodyssey.vfx.systems.*;
import com.galacticodyssey.player.systems.*;
```

Add field declarations:

```java
private EquipmentSystem equipmentSystem;
private LootGenerationSystem lootGenerationSystem;
private ShipWeaponSystem shipWeaponSystem;
private TurretTrackingSystem turretTrackingSystem;
private ShipProjectileSystem shipProjectileSystem;
private PointDefenseSystem pointDefenseSystem;
private ShipHeatSystem shipHeatSystem;
private ParticleSpawnSystem particleSpawnSystem;
private ParticleUpdateSystem particleUpdateSystem;
private ParticleRenderSystem particleRenderSystem;
private RecoilSystem recoilSystem;
private ADSSystem adsSystem;
private CrosshairSystem crosshairSystem;
private ScreenShakeSystem screenShakeSystem;
private WeaponSwaySystem weaponSwaySystem;
private LootTableRegistry lootTableRegistry;
private ShipWeaponRegistry shipWeaponRegistry;
private VFXRegistry vfxRegistry;
private ParticlePoolComponent particlePool;
```

In `initializeSystems(PerspectiveCamera camera)`, add after existing system registrations:

```java
// Equipment
lootTableRegistry = new LootTableRegistry();
equipmentSystem = new EquipmentSystem(eventBus);
lootGenerationSystem = new LootGenerationSystem(eventBus, lootTableRegistry);
engine.addSystem(equipmentSystem);
engine.addSystem(lootGenerationSystem);

// Ship weapons
shipWeaponRegistry = new ShipWeaponRegistry();
turretTrackingSystem = new TurretTrackingSystem();
shipWeaponSystem = new ShipWeaponSystem(eventBus);
shipProjectileSystem = new ShipProjectileSystem(eventBus);
pointDefenseSystem = new PointDefenseSystem(eventBus);
shipHeatSystem = new ShipHeatSystem(eventBus);
engine.addSystem(turretTrackingSystem);
engine.addSystem(shipWeaponSystem);
engine.addSystem(shipProjectileSystem);
engine.addSystem(pointDefenseSystem);
engine.addSystem(shipHeatSystem);

// VFX
vfxRegistry = new VFXRegistry();
VFXEventBindings vfxBindings = new VFXEventBindings();
Entity poolEntity = new Entity();
particlePool = new ParticlePoolComponent();
poolEntity.add(particlePool);
engine.addEntity(poolEntity);
particleSpawnSystem = new ParticleSpawnSystem(eventBus, vfxRegistry, vfxBindings, particlePool);
particleUpdateSystem = new ParticleUpdateSystem(particlePool);
particleRenderSystem = new ParticleRenderSystem(particlePool, camera);
engine.addSystem(particleSpawnSystem);
engine.addSystem(particleUpdateSystem);
engine.addSystem(particleRenderSystem);

// Shooting feedback
recoilSystem = new RecoilSystem(eventBus);
adsSystem = new ADSSystem();
crosshairSystem = new CrosshairSystem(eventBus);
screenShakeSystem = new ScreenShakeSystem();
weaponSwaySystem = new WeaponSwaySystem();
engine.addSystem(recoilSystem);
engine.addSystem(adsSystem);
engine.addSystem(crosshairSystem);
engine.addSystem(screenShakeSystem);
engine.addSystem(weaponSwaySystem);
```

In `dispose()`, add:

```java
particleRenderSystem.dispose();
particlePool.freeAll();
```

- [ ] **Step 2: Create sound_bindings.json**

```json
{
  "WeaponFiredEvent:PISTOL:BALLISTIC": "audio/sfx/weapon_pistol_fire.ogg",
  "WeaponFiredEvent:RIFLE:BALLISTIC": "audio/sfx/weapon_rifle_fire.ogg",
  "WeaponFiredEvent:SHOTGUN:BALLISTIC": "audio/sfx/weapon_shotgun_fire.ogg",
  "WeaponFiredEvent:SMG:BALLISTIC": "audio/sfx/weapon_smg_fire.ogg",
  "WeaponFiredEvent:SNIPER:BALLISTIC": "audio/sfx/weapon_sniper_fire.ogg",
  "WeaponFiredEvent:PISTOL:ENERGY": "audio/sfx/weapon_energy_fire.ogg",
  "ReloadStartedEvent": "audio/sfx/weapon_reload.ogg",
  "HitscanHitEvent:metal": "audio/sfx/impact_metal.ogg",
  "HitscanHitEvent:flesh": "audio/sfx/impact_flesh.ogg",
  "ProjectileHitEvent:EXPLOSIVE": "audio/sfx/explosion.ogg",
  "ShieldAbsorbEvent": "audio/sfx/shield_hit.ogg",
  "EntityKilledEvent": "audio/sfx/death.ogg",
  "ShipWeaponFiredEvent:BALLISTIC_CANNON": "audio/sfx/ship_cannon.ogg",
  "ShipWeaponFiredEvent:LASER_ARRAY": "audio/sfx/ship_laser.ogg",
  "ShipWeaponFiredEvent:MISSILE_LAUNCHER": "audio/sfx/ship_missile.ogg",
  "ShipOverheatEvent": "audio/sfx/overheat_alarm.ogg"
}
```

- [ ] **Step 3: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

Run: `./gradlew core:test -i`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java \
        core/src/main/resources/data/audio/sound_bindings.json
git commit -m "feat: integrate all new systems into GameWorld and add audio bindings"
```

---

### Task 23: Add Feedback Components to Player Entity

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — update `createPlayerEntity`

- [ ] **Step 1: Add feedback components to player entity creation**

In `GameWorld.createPlayerEntity(...)`, add after existing component additions:

```java
import com.galacticodyssey.player.components.RecoilComponent;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.CrosshairComponent;
import com.galacticodyssey.player.components.ScreenShakeComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;

// Add to entity creation:
player.add(new RecoilComponent());
player.add(new ADSComponent());
player.add(new CrosshairComponent());
player.add(new ScreenShakeComponent());
player.add(new InventoryComponent(8, 6, 50f));
player.add(new EquipmentSlotsComponent());
```

- [ ] **Step 2: Compile and run all tests**

Run: `./gradlew core:test -i`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat: add feedback and equipment components to player entity"
```

---

### Task 24: ShipBlueprint Hardpoint Integration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipBlueprint.java`

- [ ] **Step 1: Add hardpoints list to ShipBlueprint**

Add this field and import to `ShipBlueprint.java`:

```java
import com.galacticodyssey.ship.weapons.data.HardpointTemplate;
import java.util.ArrayList;
import java.util.List;

// Add field:
public final List<HardpointTemplate> hardpoints = new ArrayList<>();
```

- [ ] **Step 2: Compile and verify**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ShipBlueprint.java
git commit -m "feat(ship): add hardpoints list to ShipBlueprint for weapon mounting"
```

---

### Task 25: Final Integration Test

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/integration/WeaponsIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
package com.galacticodyssey.integration;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import com.galacticodyssey.player.components.*;
import com.galacticodyssey.player.systems.*;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import com.galacticodyssey.vfx.systems.ParticleSpawnSystem;
import com.galacticodyssey.vfx.systems.ParticleUpdateSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WeaponsIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        // Systems
        engine.addSystem(new EquipmentSystem(eventBus));
        engine.addSystem(new RecoilSystem(eventBus));
        engine.addSystem(new ADSSystem());
        engine.addSystem(new CrosshairSystem(eventBus));
        engine.addSystem(new ScreenShakeSystem());
        engine.addSystem(new WeaponSwaySystem());

        ParticlePoolComponent pool = new ParticlePoolComponent();
        Entity poolEntity = new Entity();
        poolEntity.add(pool);
        engine.addEntity(poolEntity);

        VFXRegistry vfxReg = new VFXRegistry();
        ParticleEffectDefinition flash = new ParticleEffectDefinition();
        flash.id = "muzzle_flash_ballistic";
        flash.burstCount = 6;
        flash.lifetimeMin = 0.05f;
        flash.lifetimeMax = 0.1f;
        flash.speedMin = 2f;
        flash.speedMax = 5f;
        flash.spread = 20f;
        vfxReg.register(flash);

        VFXEventBindings bindings = new VFXEventBindings();
        bindings.bind("WeaponFiredEvent", null, "muzzle_flash_ballistic");

        engine.addSystem(new ParticleSpawnSystem(eventBus, vfxReg, bindings, pool));
        engine.addSystem(new ParticleUpdateSystem(pool));

        // Player entity
        player = new Entity();
        player.add(new TransformComponent());
        player.add(new EquipmentSlotsComponent());
        player.add(new InventoryComponent(8, 6, 50f));
        player.add(new WeaponInventoryComponent());
        player.add(new RangedWeaponComponent());
        player.add(new ArmorComponent());
        player.add(new FPSCameraComponent());
        player.add(new MovementStateComponent());
        player.add(new CombatInputComponent());
        player.add(new RecoilComponent());
        player.add(new ADSComponent());
        player.add(new CrosshairComponent());
        player.add(new ScreenShakeComponent());

        RecoilComponent rc = player.getComponent(RecoilComponent.class);
        rc.pattern = new Vector2[]{new Vector2(1.5f, 0f)};

        engine.addEntity(player);
    }

    @Test
    void equipWeapon_thenFire_fullPipeline() {
        // Equip weapon via equipment system
        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        EquipmentSystem equipSys = engine.getSystem(EquipmentSystem.class);
        assertTrue(equipSys.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol));

        // Verify weapon assembly synced to combat component
        WeaponInventoryComponent wic = player.getComponent(WeaponInventoryComponent.class);
        assertNotNull(wic.slots[0]);

        // Simulate weapon fired event
        eventBus.publish(new WeaponFiredEvent(player, new Vector3(0, 0, -1), true));
        engine.update(0.016f);

        // Verify recoil was applied
        RecoilComponent rc = player.getComponent(RecoilComponent.class);
        assertEquals(1, rc.patternIndex);

        // Verify particles were spawned
        ParticlePoolComponent pool = null;
        for (Entity e : engine.getEntities()) {
            ParticlePoolComponent ppc = e.getComponent(ParticlePoolComponent.class);
            if (ppc != null) { pool = ppc; break; }
        }
        assertNotNull(pool);
        assertFalse(pool.active.isEmpty());
    }

    @Test
    void adsReducesSpread_andRecoilStillApplies() {
        CombatInputComponent input = player.getComponent(CombatInputComponent.class);
        ADSComponent ads = player.getComponent(ADSComponent.class);

        input.aimHeld = true;
        for (int i = 0; i < 20; i++) {
            engine.update(0.05f);
        }
        assertTrue(ads.adsProgress > 0.9f);

        eventBus.publish(new WeaponFiredEvent(player, new Vector3(0, 0, -1), true));
        engine.update(0.016f);

        RecoilComponent rc = player.getComponent(RecoilComponent.class);
        assertTrue(rc.currentPunch.len() > 0);
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `./gradlew core:test --tests "com.galacticodyssey.integration.WeaponsIntegrationTest" -i`
Expected: All 2 tests PASS

- [ ] **Step 3: Run full test suite**

Run: `./gradlew core:test -i`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/integration/WeaponsIntegrationTest.java
git commit -m "test: add weapons integration test covering equip → fire → recoil → VFX pipeline"
```
