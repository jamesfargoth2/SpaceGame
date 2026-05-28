# Refining Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the three-tier material conversion refining pipeline (raw → processed → refined) with ship/station refineries, time-based job queuing, skill yield bonuses, and full persistence.

**Architecture:** New `crafting` package houses refining domain objects (recipes, jobs, registries), ECS components (`RefineryComponent`, `MaterialStorageComponent`), and systems (`RefiningSystem`, `RefiningRequestHandler`). `MaterialItem` lives in the existing `equipment.items` package alongside other Item subclasses. All communication flows through the existing `EventBus`. No ReactorComponent exists yet — power integration is stubbed with a check that gracefully skips when absent.

**Tech Stack:** Java 17, libGDX (Ashley ECS, Json/JsonReader for data loading), JUnit 5 + Mockito for testing, Gradle build.

**Spec:** [Refining Pipeline Design Spec](../../specs/2026-05-27-refining-pipeline-design.md)

---

## File Structure

### New files — `crafting` package

```
core/src/main/java/com/galacticodyssey/crafting/
├── MaterialTier.java                    # RAW/PROCESSED/REFINED enum
├── MaterialCategory.java                # METAL/MINERAL/ORGANIC/CHEMICAL/EXOTIC/ALIEN enum
├── RecipeCategory.java                  # PROCESSING/REFINEMENT/ALLOY enum
├── RefiningJobState.java                # QUEUED/ACTIVE/PAUSED/COMPLETE/CANCELLED enum
├── RefiningFailureReason.java           # Validation failure reasons enum
├── RecipeInput.java                     # materialId + quantity pair
├── RecipeOutput.java                    # materialId + baseQuantity pair
├── RefiningJob.java                     # Job state: recipe, progress, inputs/outputs
├── RefiningConfig.java                  # Tunable constants (yield bonus multiplier)
├── components/
│   ├── MaterialStorageComponent.java    # Bulk material storage (Map<String,Integer>)
│   └── RefineryComponent.java           # Refinery tier, queue, speed
├── data/
│   ├── MaterialDefinition.java          # Data model for one material
│   ├── MaterialRegistry.java            # Loads/stores all MaterialDefinitions
│   ├── RefiningRecipe.java              # Data model for one recipe
│   ├── RefiningRecipeRegistry.java      # Loads/stores all RefiningRecipes
│   ├── RefineryModuleDefinition.java    # Data model for ship refinery module tier
│   └── RefineryModuleRegistry.java      # Loads/stores all module definitions
├── events/
│   ├── RefiningRequestEvent.java
│   ├── RefiningStartedEvent.java
│   ├── RefiningCompletedEvent.java
│   ├── RefiningFailedEvent.java
│   ├── RefiningCancelledEvent.java
│   ├── RefiningQueueChangedEvent.java
│   ├── RefiningPausedEvent.java
│   └── RefiningResumedEvent.java
└── systems/
    ├── SkillProvider.java               # Interface for querying skill levels
    ├── DefaultSkillProvider.java         # Returns 0 for all skills
    ├── RefiningRequestHandler.java       # Validates + queues refining requests
    └── RefiningSystem.java              # Advances active jobs each tick
```

### New files — persistence snapshots

```
core/src/main/java/com/galacticodyssey/persistence/snapshots/
├── MaterialStorageSnapshot.java
├── RefiningJobSnapshot.java
└── RefinerySnapshot.java
```

### New files — data

```
core/src/main/resources/data/crafting/
├── materials.json
├── refining_recipes.json
├── refinery_modules.json
└── refining_config.json
```

### Modified files

```
core/src/main/java/com/galacticodyssey/equipment/EquipmentEnums.java    # Add MATERIAL to ItemType
core/src/main/java/com/galacticodyssey/equipment/items/Item.java        # Add MATERIAL case in fromItemSnapshot
core/src/main/java/com/galacticodyssey/equipment/items/MaterialItem.java # New Item subclass (in equipment.items)
```

### Test files

```
core/src/test/java/com/galacticodyssey/crafting/
├── MaterialItemTest.java
├── MaterialRegistryTest.java
├── MaterialStorageComponentTest.java
├── RefiningRecipeRegistryTest.java
├── RefiningJobTest.java
├── SkillYieldBonusTest.java
├── RefiningRequestHandlerTest.java
├── RefiningSystemTest.java
└── RefiningPipelineIntegrationTest.java
```

---

## Task 1: Material Enums and MaterialItem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/MaterialTier.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/MaterialCategory.java`
- Create: `core/src/main/java/com/galacticodyssey/equipment/items/MaterialItem.java`
- Modify: `core/src/main/java/com/galacticodyssey/equipment/EquipmentEnums.java`
- Modify: `core/src/main/java/com/galacticodyssey/equipment/items/Item.java`
- Test: `core/src/test/java/com/galacticodyssey/crafting/MaterialItemTest.java`

- [ ] **Step 1: Write the MaterialItem test**

```java
package com.galacticodyssey.crafting;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.equipment.items.MaterialItem;
import com.galacticodyssey.persistence.snapshots.ItemSnapshot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialItemTest {

    @Test
    void getType_returnsMaterial() {
        MaterialItem item = createIronOre();
        assertEquals(ItemType.MATERIAL, item.getType());
    }

    @Test
    void materialFields_returnCorrectValues() {
        MaterialItem item = createIronOre();
        assertEquals("iron_ore", item.getMaterialId());
        assertEquals(MaterialTier.RAW, item.getTier());
        assertEquals(MaterialCategory.METAL, item.getCategory());
        assertEquals("iron_ore", item.getCommodityLink());
    }

    @Test
    void snapshotRoundTrip_preservesAllFields() {
        MaterialItem original = createIronOre();
        original.currentStack = 15;

        ItemSnapshot snapshot = original.toItemSnapshot();
        assertEquals("MATERIAL", snapshot.itemType);

        Item restored = Item.fromItemSnapshot(snapshot);
        assertInstanceOf(MaterialItem.class, restored);

        MaterialItem mat = (MaterialItem) restored;
        assertEquals("iron_ore", mat.getMaterialId());
        assertEquals(MaterialTier.RAW, mat.getTier());
        assertEquals(MaterialCategory.METAL, mat.getCategory());
        assertEquals("iron_ore", mat.getCommodityLink());
        assertEquals(15, mat.currentStack);
    }

    @Test
    void snapshotRoundTrip_nullCommodityLink_preserved() {
        MaterialItem item = new MaterialItem("iron_concentrate", "Iron Concentrate",
            "Purified iron compound", "iron_conc_icon", QualityTier.COMMON,
            1.5f, 99, "iron_concentrate", MaterialTier.PROCESSED,
            MaterialCategory.METAL, null);

        ItemSnapshot snapshot = item.toItemSnapshot();
        MaterialItem restored = (MaterialItem) Item.fromItemSnapshot(snapshot);
        assertNull(restored.getCommodityLink());
    }

    @Test
    void canStackWith_sameMaterial_returnsTrue() {
        MaterialItem a = createIronOre();
        a.currentStack = 10;
        MaterialItem b = createIronOre();
        b.currentStack = 5;
        assertTrue(a.canStackWith(b));
    }

    @Test
    void canStackWith_differentMaterial_returnsFalse() {
        MaterialItem iron = createIronOre();
        MaterialItem copper = new MaterialItem("copper_ore", "Copper Ore", "Raw copper",
            "copper_icon", QualityTier.COMMON, 2.0f, 99,
            "copper_ore", MaterialTier.RAW, MaterialCategory.METAL, "copper_ore");
        assertFalse(iron.canStackWith(copper));
    }

    private MaterialItem createIronOre() {
        return new MaterialItem("iron_ore", "Iron Ore", "Unprocessed iron-bearing rock",
            "iron_ore_icon", QualityTier.COMMON, 2.0f, 99,
            "iron_ore", MaterialTier.RAW, MaterialCategory.METAL, "iron_ore");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.MaterialItemTest" --info`
Expected: Compilation failure — MaterialTier, MaterialCategory, MaterialItem don't exist yet.

- [ ] **Step 3: Create MaterialTier enum**

```java
package com.galacticodyssey.crafting;

public enum MaterialTier {
    RAW(1),
    PROCESSED(2),
    REFINED(3);

    public final int level;

    MaterialTier(int level) {
        this.level = level;
    }
}
```

- [ ] **Step 4: Create MaterialCategory enum**

```java
package com.galacticodyssey.crafting;

public enum MaterialCategory {
    METAL,
    MINERAL,
    ORGANIC,
    CHEMICAL,
    EXOTIC,
    ALIEN
}
```

- [ ] **Step 5: Add MATERIAL to ItemType enum**

In `core/src/main/java/com/galacticodyssey/equipment/EquipmentEnums.java`, add `MATERIAL` to the `ItemType` enum after `JUNK`:

```java
public enum ItemType {
    WEAPON,
    MELEE_WEAPON,
    ARMOR,
    AMMO,
    MOD,
    COMPONENT,
    CONSUMABLE,
    JUNK,
    MATERIAL
}
```

- [ ] **Step 6: Create MaterialItem class**

```java
package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

import java.util.Map;

public class MaterialItem extends Item {
    private final String materialId;
    private final MaterialTier tier;
    private final MaterialCategory category;
    private final String commodityLink;

    public MaterialItem(String id, String name, String description, String icon,
                        QualityTier qualityTier, float weight, int maxStack,
                        String materialId, MaterialTier tier, MaterialCategory category,
                        String commodityLink) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, maxStack);
        this.materialId = materialId;
        this.tier = tier;
        this.category = category;
        this.commodityLink = commodityLink;
    }

    @Override
    public ItemType getType() {
        return ItemType.MATERIAL;
    }

    @Override
    protected void populateCustomData(Map<String, Object> customData) {
        customData.put("materialId", materialId);
        customData.put("tier", tier.name());
        customData.put("category", category.name());
        if (commodityLink != null) {
            customData.put("commodityLink", commodityLink);
        }
    }

    public String getMaterialId() { return materialId; }
    public MaterialTier getTier() { return tier; }
    public MaterialCategory getCategory() { return category; }
    public String getCommodityLink() { return commodityLink; }
}
```

- [ ] **Step 7: Add MATERIAL case to Item.fromItemSnapshot**

In `core/src/main/java/com/galacticodyssey/equipment/items/Item.java`, add this case to the switch in `fromItemSnapshot()`, after the `"JUNK"` case:

```java
case "MATERIAL": {
    String materialId = (String) cd.get("materialId");
    MaterialTier tier = MaterialTier.valueOf((String) cd.get("tier"));
    MaterialCategory category = MaterialCategory.valueOf((String) cd.get("category"));
    String commodityLink = (String) cd.get("commodityLink");
    MaterialItem item = new MaterialItem(s.itemId, s.displayName, "",
        "", quality, s.weight, s.maxStack,
        materialId, tier, category, commodityLink);
    item.currentStack = s.stackCount;
    return item;
}
```

Add these imports at the top of `Item.java`:
```java
import com.galacticodyssey.crafting.MaterialTier;
import com.galacticodyssey.crafting.MaterialCategory;
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.MaterialItemTest" --info`
Expected: All 6 tests PASS.

- [ ] **Step 9: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/MaterialTier.java \
  core/src/main/java/com/galacticodyssey/crafting/MaterialCategory.java \
  core/src/main/java/com/galacticodyssey/equipment/items/MaterialItem.java \
  core/src/main/java/com/galacticodyssey/equipment/EquipmentEnums.java \
  core/src/main/java/com/galacticodyssey/equipment/items/Item.java \
  core/src/test/java/com/galacticodyssey/crafting/MaterialItemTest.java
git commit -m "feat(crafting): add MaterialItem with tier/category enums and snapshot support"
```

---

## Task 2: Material Data Model and Registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/data/MaterialDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/data/MaterialRegistry.java`
- Create: `core/src/main/resources/data/crafting/materials.json`
- Test: `core/src/test/java/com/galacticodyssey/crafting/MaterialRegistryTest.java`

- [ ] **Step 1: Write the MaterialRegistry test**

```java
package com.galacticodyssey.crafting;

import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialRegistryTest {

    private MaterialRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaterialRegistry();
        registry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99,
            "Unprocessed iron-bearing rock", "iron_ore"));
        registry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99,
            "Purified iron compound", null));
        registry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50,
            "Smelted iron bar", null));
        registry.register(new MaterialDefinition("carbon_deposit", "Carbon Deposit",
            MaterialTier.RAW, MaterialCategory.MINERAL, 1.8f, 1.2f, 99,
            "Raw carbon material", "carbon"));
    }

    @Test
    void get_existingId_returnsMaterial() {
        MaterialDefinition def = registry.get("iron_ore");
        assertNotNull(def);
        assertEquals("Iron Ore", def.name);
        assertEquals(MaterialTier.RAW, def.tier);
        assertEquals(MaterialCategory.METAL, def.category);
    }

    @Test
    void get_unknownId_returnsNull() {
        assertNull(registry.get("unobtanium"));
    }

    @Test
    void getByTier_returnsCorrectMaterials() {
        assertEquals(2, registry.getByTier(MaterialTier.RAW).size());
        assertEquals(1, registry.getByTier(MaterialTier.PROCESSED).size());
        assertEquals(1, registry.getByTier(MaterialTier.REFINED).size());
    }

    @Test
    void getByCategory_returnsCorrectMaterials() {
        assertEquals(3, registry.getByCategory(MaterialCategory.METAL).size());
        assertEquals(1, registry.getByCategory(MaterialCategory.MINERAL).size());
        assertEquals(0, registry.getByCategory(MaterialCategory.EXOTIC).size());
    }

    @Test
    void getAll_returnsAll() {
        assertEquals(4, registry.getAll().size());
    }

    @Test
    void register_duplicateId_throwsException() {
        MaterialDefinition dupe = new MaterialDefinition("iron_ore", "Dupe",
            MaterialTier.RAW, MaterialCategory.METAL, 1.0f, 1.0f, 99, "", null);
        assertThrows(IllegalArgumentException.class, () -> registry.register(dupe));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.MaterialRegistryTest" --info`
Expected: Compilation failure — MaterialDefinition, MaterialRegistry don't exist.

- [ ] **Step 3: Create MaterialDefinition**

```java
package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;

public class MaterialDefinition {
    public final String materialId;
    public final String name;
    public final MaterialTier tier;
    public final MaterialCategory category;
    public final float weight;
    public final float volume;
    public final int maxStack;
    public final String description;
    public final String commodityLink;

    public MaterialDefinition(String materialId, String name, MaterialTier tier,
                              MaterialCategory category, float weight, float volume,
                              int maxStack, String description, String commodityLink) {
        this.materialId = materialId;
        this.name = name;
        this.tier = tier;
        this.category = category;
        this.weight = weight;
        this.volume = volume;
        this.maxStack = maxStack;
        this.description = description;
        this.commodityLink = commodityLink;
    }

    public MaterialDefinition() {
        this("", "", MaterialTier.RAW, MaterialCategory.METAL, 0, 0, 1, "", null);
    }
}
```

- [ ] **Step 4: Create MaterialRegistry**

```java
package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MaterialRegistry {
    private final Map<String, MaterialDefinition> byId = new HashMap<>();

    public void register(MaterialDefinition definition) {
        if (byId.containsKey(definition.materialId)) {
            throw new IllegalArgumentException(
                "Duplicate material ID: " + definition.materialId);
        }
        byId.put(definition.materialId, definition);
    }

    public MaterialDefinition get(String materialId) {
        return byId.get(materialId);
    }

    public List<MaterialDefinition> getByTier(MaterialTier tier) {
        return byId.values().stream()
            .filter(d -> d.tier == tier)
            .collect(Collectors.toList());
    }

    public List<MaterialDefinition> getByCategory(MaterialCategory category) {
        return byId.values().stream()
            .filter(d -> d.category == category)
            .collect(Collectors.toList());
    }

    public List<MaterialDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    public boolean contains(String materialId) {
        return byId.containsKey(materialId);
    }

    public void loadFromFile() {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/materials.json"));
        JsonValue materials = root.get("materials");
        for (JsonValue entry = materials.child; entry != null; entry = entry.next) {
            MaterialDefinition def = new MaterialDefinition(
                entry.getString("materialId"),
                entry.getString("name"),
                MaterialTier.valueOf(entry.getString("tier")),
                MaterialCategory.valueOf(entry.getString("category")),
                entry.getFloat("weight"),
                entry.getFloat("volume"),
                entry.getInt("maxStack"),
                entry.getString("description", ""),
                entry.getString("commodityLink", null)
            );
            register(def);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.MaterialRegistryTest" --info`
Expected: All 6 tests PASS.

- [ ] **Step 6: Create materials.json data file**

Create `core/src/main/resources/data/crafting/materials.json`:

```json
{
  "materials": [
    {
      "materialId": "iron_ore",
      "name": "Iron Ore",
      "tier": "RAW",
      "category": "METAL",
      "weight": 2.0,
      "volume": 1.0,
      "maxStack": 99,
      "description": "Unprocessed iron-bearing rock",
      "commodityLink": "iron_ore"
    },
    {
      "materialId": "iron_concentrate",
      "name": "Iron Concentrate",
      "tier": "PROCESSED",
      "category": "METAL",
      "weight": 1.5,
      "volume": 0.8,
      "maxStack": 99,
      "description": "Purified iron compound"
    },
    {
      "materialId": "iron_ingot",
      "name": "Iron Ingot",
      "tier": "REFINED",
      "category": "METAL",
      "weight": 1.0,
      "volume": 0.5,
      "maxStack": 50,
      "description": "Smelted iron bar, ready for fabrication"
    },
    {
      "materialId": "titanium_ore",
      "name": "Titanium Ore",
      "tier": "RAW",
      "category": "METAL",
      "weight": 2.5,
      "volume": 1.0,
      "maxStack": 99,
      "description": "Dense titanium-bearing mineral",
      "commodityLink": "titanium"
    },
    {
      "materialId": "titanium_sponge",
      "name": "Titanium Sponge",
      "tier": "PROCESSED",
      "category": "METAL",
      "weight": 1.8,
      "volume": 0.9,
      "maxStack": 99,
      "description": "Porous metallic titanium"
    },
    {
      "materialId": "titanium_plate",
      "name": "Titanium Plate",
      "tier": "REFINED",
      "category": "METAL",
      "weight": 1.2,
      "volume": 0.4,
      "maxStack": 50,
      "description": "High-strength titanium sheet"
    },
    {
      "materialId": "carbon_deposit",
      "name": "Carbon Deposit",
      "tier": "RAW",
      "category": "MINERAL",
      "weight": 1.8,
      "volume": 1.2,
      "maxStack": 99,
      "description": "Raw carbonaceous material",
      "commodityLink": "carbon"
    },
    {
      "materialId": "carbon_powder",
      "name": "Carbon Powder",
      "tier": "PROCESSED",
      "category": "MINERAL",
      "weight": 0.8,
      "volume": 0.6,
      "maxStack": 99,
      "description": "Finely ground carbon"
    },
    {
      "materialId": "carbon_fiber",
      "name": "Carbon Fiber",
      "tier": "REFINED",
      "category": "MINERAL",
      "weight": 0.5,
      "volume": 0.3,
      "maxStack": 50,
      "description": "Woven carbon composite strands"
    },
    {
      "materialId": "lithium_ore",
      "name": "Lithium Ore",
      "tier": "RAW",
      "category": "MINERAL",
      "weight": 1.5,
      "volume": 1.0,
      "maxStack": 99,
      "description": "Lithium-bearing spodumene",
      "commodityLink": "lithium"
    },
    {
      "materialId": "lithium_carbonate",
      "name": "Lithium Carbonate",
      "tier": "PROCESSED",
      "category": "MINERAL",
      "weight": 1.0,
      "volume": 0.7,
      "maxStack": 99,
      "description": "Lithium salt compound"
    },
    {
      "materialId": "lithium_cell",
      "name": "Lithium Cell",
      "tier": "REFINED",
      "category": "MINERAL",
      "weight": 0.8,
      "volume": 0.4,
      "maxStack": 50,
      "description": "High-capacity lithium power cell"
    },
    {
      "materialId": "copper_ore",
      "name": "Copper Ore",
      "tier": "RAW",
      "category": "METAL",
      "weight": 2.2,
      "volume": 1.0,
      "maxStack": 99,
      "description": "Raw chalcopyrite copper ore",
      "commodityLink": "copper"
    },
    {
      "materialId": "copper_wire",
      "name": "Copper Wire",
      "tier": "PROCESSED",
      "category": "METAL",
      "weight": 0.6,
      "volume": 0.3,
      "maxStack": 99,
      "description": "Drawn copper conductor wire"
    },
    {
      "materialId": "copper_coil",
      "name": "Copper Coil",
      "tier": "REFINED",
      "category": "METAL",
      "weight": 1.0,
      "volume": 0.5,
      "maxStack": 50,
      "description": "Precision-wound electromagnetic coil"
    },
    {
      "materialId": "steel_alloy",
      "name": "Steel Alloy",
      "tier": "REFINED",
      "category": "METAL",
      "weight": 1.2,
      "volume": 0.5,
      "maxStack": 50,
      "description": "Iron-carbon alloy with superior strength"
    }
  ]
}
```

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/data/MaterialDefinition.java \
  core/src/main/java/com/galacticodyssey/crafting/data/MaterialRegistry.java \
  core/src/main/resources/data/crafting/materials.json \
  core/src/test/java/com/galacticodyssey/crafting/MaterialRegistryTest.java
git commit -m "feat(crafting): add MaterialDefinition, MaterialRegistry, and starter materials data"
```

---

## Task 3: MaterialStorageComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/components/MaterialStorageComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/MaterialStorageSnapshot.java`
- Test: `core/src/test/java/com/galacticodyssey/crafting/MaterialStorageComponentTest.java`

- [ ] **Step 1: Write the MaterialStorageComponent test**

```java
package com.galacticodyssey.crafting;

import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.persistence.snapshots.MaterialStorageSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialStorageComponentTest {

    private MaterialStorageComponent storage;
    private MaterialRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaterialRegistry();
        registry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        registry.register(new MaterialDefinition("copper_ore", "Copper Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.2f, 1.0f, 99, "", "copper"));
        registry.register(new MaterialDefinition("carbon_deposit", "Carbon Deposit",
            MaterialTier.RAW, MaterialCategory.MINERAL, 1.8f, 1.2f, 99, "", "carbon"));
        storage = new MaterialStorageComponent(100f, 80f, registry);
    }

    @Test
    void tryAdd_withinCapacity_succeeds() {
        assertTrue(storage.tryAdd("iron_ore", 10));
        assertEquals(10, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryAdd_exceedsWeightCapacity_fails() {
        // 51 iron ore * 2.0 weight = 102 > 100
        assertFalse(storage.tryAdd("iron_ore", 51));
        assertEquals(0, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryAdd_exceedsVolumeCapacity_fails() {
        // 81 carbon * 1.2 volume = 97.2 > 80
        assertFalse(storage.tryAdd("carbon_deposit", 81));
        assertEquals(0, storage.getQuantity("carbon_deposit"));
    }

    @Test
    void tryAdd_multipleMaterials_tracksIndependently() {
        storage.tryAdd("iron_ore", 5);
        storage.tryAdd("copper_ore", 3);
        assertEquals(5, storage.getQuantity("iron_ore"));
        assertEquals(3, storage.getQuantity("copper_ore"));
    }

    @Test
    void tryAdd_unknownMaterial_fails() {
        assertFalse(storage.tryAdd("unobtanium", 1));
    }

    @Test
    void hasEnough_sufficient_returnsTrue() {
        storage.tryAdd("iron_ore", 10);
        assertTrue(storage.hasEnough("iron_ore", 5));
        assertTrue(storage.hasEnough("iron_ore", 10));
    }

    @Test
    void hasEnough_insufficient_returnsFalse() {
        storage.tryAdd("iron_ore", 3);
        assertFalse(storage.hasEnough("iron_ore", 5));
    }

    @Test
    void hasEnough_noStock_returnsFalse() {
        assertFalse(storage.hasEnough("iron_ore", 1));
    }

    @Test
    void tryConsume_sufficient_removesAndReturnsTrue() {
        storage.tryAdd("iron_ore", 10);
        assertTrue(storage.tryConsume("iron_ore", 4));
        assertEquals(6, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryConsume_exactAmount_removesEntry() {
        storage.tryAdd("iron_ore", 5);
        assertTrue(storage.tryConsume("iron_ore", 5));
        assertEquals(0, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryConsume_insufficient_doesNothingAndReturnsFalse() {
        storage.tryAdd("iron_ore", 3);
        assertFalse(storage.tryConsume("iron_ore", 5));
        assertEquals(3, storage.getQuantity("iron_ore"));
    }

    @Test
    void getCurrentWeight_sumsCorrectly() {
        storage.tryAdd("iron_ore", 10);     // 10 * 2.0 = 20
        storage.tryAdd("copper_ore", 5);    // 5 * 2.2 = 11
        assertEquals(31.0f, storage.getCurrentWeight(), 0.01f);
    }

    @Test
    void getCurrentVolume_sumsCorrectly() {
        storage.tryAdd("iron_ore", 10);         // 10 * 1.0 = 10
        storage.tryAdd("carbon_deposit", 5);    // 5 * 1.2 = 6
        assertEquals(16.0f, storage.getCurrentVolume(), 0.01f);
    }

    @Test
    void snapshotRoundTrip_preservesState() {
        storage.tryAdd("iron_ore", 15);
        storage.tryAdd("copper_ore", 7);

        MaterialStorageSnapshot snapshot = storage.takeSnapshot();
        assertEquals(2, snapshot.quantities.size());
        assertEquals(15, snapshot.quantities.get("iron_ore"));

        MaterialStorageComponent restored = new MaterialStorageComponent(100f, 80f, registry);
        restored.restoreFromSnapshot(snapshot);
        assertEquals(15, restored.getQuantity("iron_ore"));
        assertEquals(7, restored.getQuantity("copper_ore"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.MaterialStorageComponentTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Create MaterialStorageSnapshot**

```java
package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

public class MaterialStorageSnapshot {
    public float maxWeight;
    public float maxVolume;
    public Map<String, Integer> quantities = new HashMap<>();

    public MaterialStorageSnapshot() {}
}
```

- [ ] **Step 4: Create MaterialStorageComponent**

```java
package com.galacticodyssey.crafting.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.MaterialStorageSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MaterialStorageComponent implements Component, Snapshotable<MaterialStorageSnapshot> {
    private final Map<String, Integer> quantities = new HashMap<>();
    private final float maxWeight;
    private final float maxVolume;
    private final MaterialRegistry registry;

    public MaterialStorageComponent(float maxWeight, float maxVolume, MaterialRegistry registry) {
        this.maxWeight = maxWeight;
        this.maxVolume = maxVolume;
        this.registry = registry;
    }

    public boolean tryAdd(String materialId, int amount) {
        MaterialDefinition def = registry.get(materialId);
        if (def == null) return false;

        float addedWeight = def.weight * amount;
        float addedVolume = def.volume * amount;
        if (getCurrentWeight() + addedWeight > maxWeight) return false;
        if (getCurrentVolume() + addedVolume > maxVolume) return false;

        quantities.merge(materialId, amount, Integer::sum);
        return true;
    }

    public int getQuantity(String materialId) {
        return quantities.getOrDefault(materialId, 0);
    }

    public boolean hasEnough(String materialId, int amount) {
        return getQuantity(materialId) >= amount;
    }

    public boolean tryConsume(String materialId, int amount) {
        int current = getQuantity(materialId);
        if (current < amount) return false;
        int remaining = current - amount;
        if (remaining == 0) {
            quantities.remove(materialId);
        } else {
            quantities.put(materialId, remaining);
        }
        return true;
    }

    public float getCurrentWeight() {
        float total = 0;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            MaterialDefinition def = registry.get(entry.getKey());
            if (def != null) {
                total += def.weight * entry.getValue();
            }
        }
        return total;
    }

    public float getCurrentVolume() {
        float total = 0;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            MaterialDefinition def = registry.get(entry.getKey());
            if (def != null) {
                total += def.volume * entry.getValue();
            }
        }
        return total;
    }

    public Map<String, Integer> getAllQuantities() {
        return Collections.unmodifiableMap(quantities);
    }

    public float getMaxWeight() { return maxWeight; }
    public float getMaxVolume() { return maxVolume; }

    @Override
    public MaterialStorageSnapshot takeSnapshot() {
        MaterialStorageSnapshot snap = new MaterialStorageSnapshot();
        snap.maxWeight = maxWeight;
        snap.maxVolume = maxVolume;
        snap.quantities.putAll(quantities);
        return snap;
    }

    @Override
    public void restoreFromSnapshot(MaterialStorageSnapshot snap) {
        quantities.clear();
        quantities.putAll(snap.quantities);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.MaterialStorageComponentTest" --info`
Expected: All 14 tests PASS.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/components/MaterialStorageComponent.java \
  core/src/main/java/com/galacticodyssey/persistence/snapshots/MaterialStorageSnapshot.java \
  core/src/test/java/com/galacticodyssey/crafting/MaterialStorageComponentTest.java
git commit -m "feat(crafting): add MaterialStorageComponent with weight/volume limits and snapshot"
```

---

## Task 4: Refining Recipe Data and Registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/RecipeCategory.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/RecipeInput.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/RecipeOutput.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/data/RefiningRecipe.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/data/RefiningRecipeRegistry.java`
- Create: `core/src/main/resources/data/crafting/refining_recipes.json`
- Test: `core/src/test/java/com/galacticodyssey/crafting/RefiningRecipeRegistryTest.java`

- [ ] **Step 1: Write the RefiningRecipeRegistry test**

```java
package com.galacticodyssey.crafting;

import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningRecipeRegistryTest {

    private RefiningRecipeRegistry recipeRegistry;
    private MaterialRegistry materialRegistry;

    @BeforeEach
    void setUp() {
        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50, "", null));
        materialRegistry.register(new MaterialDefinition("carbon_powder", "Carbon Powder",
            MaterialTier.PROCESSED, MaterialCategory.MINERAL, 0.8f, 0.6f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("steel_alloy", "Steel Alloy",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.2f, 0.5f, 50, "", null));

        recipeRegistry = new RefiningRecipeRegistry();
        recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Process Iron Ore",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("iron_ore", 5)),
            List.of(new RecipeOutput("iron_concentrate", 3)),
            30.0f, 10f));
        recipeRegistry.register(new RefiningRecipe("refine_iron", "Refine Iron",
            RecipeCategory.REFINEMENT, 2,
            List.of(new RecipeInput("iron_concentrate", 4)),
            List.of(new RecipeOutput("iron_ingot", 2)),
            60.0f, 20f));
        recipeRegistry.register(new RefiningRecipe("forge_steel", "Forge Steel Alloy",
            RecipeCategory.ALLOY, 3,
            List.of(new RecipeInput("iron_ingot", 3), new RecipeInput("carbon_powder", 1)),
            List.of(new RecipeOutput("steel_alloy", 2)),
            90.0f, 30f));
    }

    @Test
    void getRecipe_existingId_returnsRecipe() {
        RefiningRecipe recipe = recipeRegistry.getRecipe("process_iron_ore");
        assertNotNull(recipe);
        assertEquals("Process Iron Ore", recipe.name);
        assertEquals(RecipeCategory.PROCESSING, recipe.category);
        assertEquals(1, recipe.requiredTier);
    }

    @Test
    void getRecipe_unknownId_returnsNull() {
        assertNull(recipeRegistry.getRecipe("unknown_recipe"));
    }

    @Test
    void getRecipesForTier_returnsUpToTier() {
        assertEquals(1, recipeRegistry.getRecipesForTier(1).size());
        assertEquals(2, recipeRegistry.getRecipesForTier(2).size());
        assertEquals(3, recipeRegistry.getRecipesForTier(3).size());
    }

    @Test
    void getRecipesProducing_returnsMatchingRecipes() {
        List<RefiningRecipe> ironConc = recipeRegistry.getRecipesProducing("iron_concentrate");
        assertEquals(1, ironConc.size());
        assertEquals("process_iron_ore", ironConc.get(0).recipeId);
    }

    @Test
    void validate_allInputsAndOutputsExist_returnsTrue() {
        assertTrue(recipeRegistry.validate(materialRegistry));
    }

    @Test
    void validate_missingMaterial_returnsFalse() {
        RefiningRecipeRegistry badRegistry = new RefiningRecipeRegistry();
        badRegistry.register(new RefiningRecipe("bad_recipe", "Bad",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("nonexistent", 1)),
            List.of(new RecipeOutput("iron_concentrate", 1)),
            10f, 5f));
        assertFalse(badRegistry.validate(materialRegistry));
    }

    @Test
    void register_duplicateId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
            recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Dupe",
                RecipeCategory.PROCESSING, 1,
                List.of(new RecipeInput("iron_ore", 1)),
                List.of(new RecipeOutput("iron_concentrate", 1)),
                10f, 5f)));
    }

    @Test
    void recipeInputsAndOutputs_correctValues() {
        RefiningRecipe steel = recipeRegistry.getRecipe("forge_steel");
        assertEquals(2, steel.inputs.size());
        assertEquals("iron_ingot", steel.inputs.get(0).materialId);
        assertEquals(3, steel.inputs.get(0).quantity);
        assertEquals(1, steel.outputs.size());
        assertEquals("steel_alloy", steel.outputs.get(0).materialId);
        assertEquals(2, steel.outputs.get(0).baseQuantity);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningRecipeRegistryTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Create RecipeCategory, RecipeInput, and RecipeOutput**

`RecipeCategory.java`:
```java
package com.galacticodyssey.crafting;

public enum RecipeCategory {
    PROCESSING,
    REFINEMENT,
    ALLOY
}
```

`RecipeInput.java`:
```java
package com.galacticodyssey.crafting;

public final class RecipeInput {
    public final String materialId;
    public final int quantity;

    public RecipeInput(String materialId, int quantity) {
        this.materialId = materialId;
        this.quantity = quantity;
    }
}
```

`RecipeOutput.java`:
```java
package com.galacticodyssey.crafting;

public final class RecipeOutput {
    public final String materialId;
    public final int baseQuantity;

    public RecipeOutput(String materialId, int baseQuantity) {
        this.materialId = materialId;
        this.baseQuantity = baseQuantity;
    }
}
```

- [ ] **Step 4: Create RefiningRecipe**

```java
package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.RecipeCategory;
import com.galacticodyssey.crafting.RecipeInput;
import com.galacticodyssey.crafting.RecipeOutput;

import java.util.List;

public class RefiningRecipe {
    public final String recipeId;
    public final String name;
    public final RecipeCategory category;
    public final int requiredTier;
    public final List<RecipeInput> inputs;
    public final List<RecipeOutput> outputs;
    public final float processingTime;
    public final float powerCost;

    public RefiningRecipe(String recipeId, String name, RecipeCategory category,
                          int requiredTier, List<RecipeInput> inputs,
                          List<RecipeOutput> outputs, float processingTime,
                          float powerCost) {
        this.recipeId = recipeId;
        this.name = name;
        this.category = category;
        this.requiredTier = requiredTier;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.processingTime = processingTime;
        this.powerCost = powerCost;
    }
}
```

- [ ] **Step 5: Create RefiningRecipeRegistry**

```java
package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.RecipeCategory;
import com.galacticodyssey.crafting.RecipeInput;
import com.galacticodyssey.crafting.RecipeOutput;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RefiningRecipeRegistry {
    private final Map<String, RefiningRecipe> byId = new HashMap<>();

    public void register(RefiningRecipe recipe) {
        if (byId.containsKey(recipe.recipeId)) {
            throw new IllegalArgumentException("Duplicate recipe ID: " + recipe.recipeId);
        }
        byId.put(recipe.recipeId, recipe);
    }

    public RefiningRecipe getRecipe(String recipeId) {
        return byId.get(recipeId);
    }

    public List<RefiningRecipe> getRecipesForTier(int tier) {
        return byId.values().stream()
            .filter(r -> r.requiredTier <= tier)
            .collect(Collectors.toList());
    }

    public List<RefiningRecipe> getRecipesProducing(String materialId) {
        return byId.values().stream()
            .filter(r -> r.outputs.stream().anyMatch(o -> o.materialId.equals(materialId)))
            .collect(Collectors.toList());
    }

    public List<RefiningRecipe> getAll() {
        return new ArrayList<>(byId.values());
    }

    public boolean validate(MaterialRegistry materialRegistry) {
        for (RefiningRecipe recipe : byId.values()) {
            for (RecipeInput input : recipe.inputs) {
                if (!materialRegistry.contains(input.materialId)) return false;
            }
            for (RecipeOutput output : recipe.outputs) {
                if (!materialRegistry.contains(output.materialId)) return false;
            }
        }
        return true;
    }

    public void loadFromFile() {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/refining_recipes.json"));
        JsonValue recipes = root.get("recipes");
        for (JsonValue entry = recipes.child; entry != null; entry = entry.next) {
            List<RecipeInput> inputs = new ArrayList<>();
            for (JsonValue inp = entry.get("inputs").child; inp != null; inp = inp.next) {
                inputs.add(new RecipeInput(inp.getString("materialId"), inp.getInt("quantity")));
            }
            List<RecipeOutput> outputs = new ArrayList<>();
            for (JsonValue out = entry.get("outputs").child; out != null; out = out.next) {
                outputs.add(new RecipeOutput(out.getString("materialId"), out.getInt("baseQuantity")));
            }
            register(new RefiningRecipe(
                entry.getString("recipeId"),
                entry.getString("name"),
                RecipeCategory.valueOf(entry.getString("category")),
                entry.getInt("requiredTier"),
                inputs, outputs,
                entry.getFloat("processingTime"),
                entry.getFloat("powerCost")
            ));
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningRecipeRegistryTest" --info`
Expected: All 8 tests PASS.

- [ ] **Step 7: Create refining_recipes.json**

Create `core/src/main/resources/data/crafting/refining_recipes.json`:

```json
{
  "recipes": [
    {
      "recipeId": "process_iron_ore",
      "name": "Process Iron Ore",
      "category": "PROCESSING",
      "requiredTier": 1,
      "inputs": [{ "materialId": "iron_ore", "quantity": 5 }],
      "outputs": [{ "materialId": "iron_concentrate", "baseQuantity": 3 }],
      "processingTime": 30.0,
      "powerCost": 10
    },
    {
      "recipeId": "refine_iron",
      "name": "Refine Iron",
      "category": "REFINEMENT",
      "requiredTier": 2,
      "inputs": [{ "materialId": "iron_concentrate", "quantity": 4 }],
      "outputs": [{ "materialId": "iron_ingot", "baseQuantity": 2 }],
      "processingTime": 60.0,
      "powerCost": 20
    },
    {
      "recipeId": "forge_steel",
      "name": "Forge Steel Alloy",
      "category": "ALLOY",
      "requiredTier": 3,
      "inputs": [
        { "materialId": "iron_ingot", "quantity": 3 },
        { "materialId": "carbon_powder", "quantity": 1 }
      ],
      "outputs": [{ "materialId": "steel_alloy", "baseQuantity": 2 }],
      "processingTime": 90.0,
      "powerCost": 30
    },
    {
      "recipeId": "process_titanium_ore",
      "name": "Process Titanium Ore",
      "category": "PROCESSING",
      "requiredTier": 1,
      "inputs": [{ "materialId": "titanium_ore", "quantity": 5 }],
      "outputs": [{ "materialId": "titanium_sponge", "baseQuantity": 2 }],
      "processingTime": 45.0,
      "powerCost": 15
    },
    {
      "recipeId": "refine_titanium",
      "name": "Refine Titanium",
      "category": "REFINEMENT",
      "requiredTier": 2,
      "inputs": [{ "materialId": "titanium_sponge", "quantity": 3 }],
      "outputs": [{ "materialId": "titanium_plate", "baseQuantity": 2 }],
      "processingTime": 75.0,
      "powerCost": 25
    },
    {
      "recipeId": "process_carbon",
      "name": "Process Carbon Deposit",
      "category": "PROCESSING",
      "requiredTier": 1,
      "inputs": [{ "materialId": "carbon_deposit", "quantity": 4 }],
      "outputs": [{ "materialId": "carbon_powder", "baseQuantity": 3 }],
      "processingTime": 25.0,
      "powerCost": 8
    },
    {
      "recipeId": "refine_carbon",
      "name": "Refine Carbon Fiber",
      "category": "REFINEMENT",
      "requiredTier": 2,
      "inputs": [{ "materialId": "carbon_powder", "quantity": 5 }],
      "outputs": [{ "materialId": "carbon_fiber", "baseQuantity": 2 }],
      "processingTime": 70.0,
      "powerCost": 22
    },
    {
      "recipeId": "process_lithium_ore",
      "name": "Process Lithium Ore",
      "category": "PROCESSING",
      "requiredTier": 1,
      "inputs": [{ "materialId": "lithium_ore", "quantity": 4 }],
      "outputs": [{ "materialId": "lithium_carbonate", "baseQuantity": 2 }],
      "processingTime": 35.0,
      "powerCost": 12
    },
    {
      "recipeId": "refine_lithium",
      "name": "Refine Lithium Cell",
      "category": "REFINEMENT",
      "requiredTier": 2,
      "inputs": [{ "materialId": "lithium_carbonate", "quantity": 3 }],
      "outputs": [{ "materialId": "lithium_cell", "baseQuantity": 1 }],
      "processingTime": 80.0,
      "powerCost": 28
    },
    {
      "recipeId": "process_copper_ore",
      "name": "Process Copper Ore",
      "category": "PROCESSING",
      "requiredTier": 1,
      "inputs": [{ "materialId": "copper_ore", "quantity": 4 }],
      "outputs": [{ "materialId": "copper_wire", "baseQuantity": 3 }],
      "processingTime": 20.0,
      "powerCost": 8
    },
    {
      "recipeId": "refine_copper",
      "name": "Refine Copper Coil",
      "category": "REFINEMENT",
      "requiredTier": 2,
      "inputs": [{ "materialId": "copper_wire", "quantity": 4 }],
      "outputs": [{ "materialId": "copper_coil", "baseQuantity": 2 }],
      "processingTime": 50.0,
      "powerCost": 18
    }
  ]
}
```

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/RecipeCategory.java \
  core/src/main/java/com/galacticodyssey/crafting/RecipeInput.java \
  core/src/main/java/com/galacticodyssey/crafting/RecipeOutput.java \
  core/src/main/java/com/galacticodyssey/crafting/data/RefiningRecipe.java \
  core/src/main/java/com/galacticodyssey/crafting/data/RefiningRecipeRegistry.java \
  core/src/main/resources/data/crafting/refining_recipes.json \
  core/src/test/java/com/galacticodyssey/crafting/RefiningRecipeRegistryTest.java
git commit -m "feat(crafting): add RefiningRecipe, RefiningRecipeRegistry, and recipe data"
```

---

## Task 5: Refining Job and Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/RefiningJobState.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/RefiningJob.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/RefiningFailureReason.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningRequestEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningStartedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningCompletedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningFailedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningCancelledEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningQueueChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningPausedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/events/RefiningResumedEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/crafting/RefiningJobTest.java`

- [ ] **Step 1: Write the RefiningJob test**

```java
package com.galacticodyssey.crafting;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningJobTest {

    @Test
    void newJob_startsQueued_zeroProgress() {
        RefiningJob job = createTestJob();
        assertEquals(RefiningJobState.QUEUED, job.getState());
        assertEquals(0f, job.getProgress(), 0.001f);
    }

    @Test
    void advanceProgress_incrementsCorrectly() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(0.25f);
        assertEquals(0.25f, job.getProgress(), 0.001f);
        job.advanceProgress(0.25f);
        assertEquals(0.50f, job.getProgress(), 0.001f);
    }

    @Test
    void advanceProgress_clampedAtOne() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(1.5f);
        assertEquals(1.0f, job.getProgress(), 0.001f);
    }

    @Test
    void isComplete_trueAtFullProgress() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(1.0f);
        assertTrue(job.isComplete());
    }

    @Test
    void isComplete_falseWhenPartial() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(0.99f);
        assertFalse(job.isComplete());
    }

    @Test
    void calculateReturnedInputs_returnsProportionalToRemaining() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(0.6f);
        // 40% remaining of 5 iron_ore = 2 (floored)
        Map<String, Integer> returned = job.calculateReturnedInputs();
        assertEquals(2, returned.get("iron_ore"));
    }

    @Test
    void calculateReturnedInputs_queuedJob_returnsAll() {
        RefiningJob job = createTestJob();
        Map<String, Integer> returned = job.calculateReturnedInputs();
        assertEquals(5, returned.get("iron_ore"));
    }

    @Test
    void calculateReturnedInputs_completeJob_returnsNothing() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(1.0f);
        Map<String, Integer> returned = job.calculateReturnedInputs();
        assertEquals(0, returned.get("iron_ore"));
    }

    @Test
    void outputs_matchConstructorValues() {
        RefiningJob job = createTestJob();
        assertEquals(1, job.getOutputs().size());
        assertEquals("iron_concentrate", job.getOutputs().get(0).materialId);
        assertEquals(3, job.getOutputs().get(0).quantity);
    }

    private RefiningJob createTestJob() {
        return new RefiningJob(
            "process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            30.0f
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningJobTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Create RefiningJobState**

```java
package com.galacticodyssey.crafting;

public enum RefiningJobState {
    QUEUED,
    ACTIVE,
    PAUSED,
    COMPLETE,
    CANCELLED
}
```

- [ ] **Step 4: Create RefiningJob**

```java
package com.galacticodyssey.crafting;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RefiningJob {
    private final String jobId;
    private final String recipeId;
    private final Map<String, Integer> inputsConsumed;
    private final List<Output> outputs;
    private final float totalTime;
    private RefiningJobState state;
    private float progress;

    public RefiningJob(String recipeId, Map<String, Integer> inputsConsumed,
                       List<Output> outputs, float totalTime) {
        this.jobId = UUID.randomUUID().toString();
        this.recipeId = recipeId;
        this.inputsConsumed = new HashMap<>(inputsConsumed);
        this.outputs = List.copyOf(outputs);
        this.totalTime = totalTime;
        this.state = RefiningJobState.QUEUED;
        this.progress = 0f;
    }

    RefiningJob(String jobId, String recipeId, Map<String, Integer> inputsConsumed,
                List<Output> outputs, float totalTime, RefiningJobState state, float progress) {
        this.jobId = jobId;
        this.recipeId = recipeId;
        this.inputsConsumed = new HashMap<>(inputsConsumed);
        this.outputs = List.copyOf(outputs);
        this.totalTime = totalTime;
        this.state = state;
        this.progress = progress;
    }

    public void advanceProgress(float amount) {
        progress = Math.min(1.0f, progress + amount);
    }

    public boolean isComplete() {
        return progress >= 1.0f;
    }

    public Map<String, Integer> calculateReturnedInputs() {
        float remaining = 1.0f - progress;
        Map<String, Integer> returned = new HashMap<>();
        for (Map.Entry<String, Integer> entry : inputsConsumed.entrySet()) {
            returned.put(entry.getKey(), (int) Math.floor(entry.getValue() * remaining));
        }
        return returned;
    }

    public String getJobId() { return jobId; }
    public String getRecipeId() { return recipeId; }
    public Map<String, Integer> getInputsConsumed() { return Collections.unmodifiableMap(inputsConsumed); }
    public List<Output> getOutputs() { return outputs; }
    public float getTotalTime() { return totalTime; }
    public RefiningJobState getState() { return state; }
    public void setState(RefiningJobState state) { this.state = state; }
    public float getProgress() { return progress; }

    public static final class Output {
        public final String materialId;
        public final int quantity;

        public Output(String materialId, int quantity) {
            this.materialId = materialId;
            this.quantity = quantity;
        }
    }
}
```

- [ ] **Step 5: Create RefiningFailureReason**

```java
package com.galacticodyssey.crafting;

public enum RefiningFailureReason {
    NO_REFINERY,
    TIER_TOO_LOW,
    QUEUE_FULL,
    INSUFFICIENT_MATERIALS,
    RECIPE_NOT_FOUND
}
```

- [ ] **Step 6: Create all event classes**

All events follow the same pattern — final class, public final fields, constructor:

`RefiningRequestEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;

public final class RefiningRequestEvent {
    public final Entity entity;
    public final String recipeId;
    public final int batchCount;

    public RefiningRequestEvent(Entity entity, String recipeId, int batchCount) {
        this.entity = entity;
        this.recipeId = recipeId;
        this.batchCount = batchCount;
    }

    public RefiningRequestEvent(Entity entity, String recipeId) {
        this(entity, recipeId, 1);
    }
}
```

`RefiningStartedEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

public final class RefiningStartedEvent {
    public final Entity entity;
    public final RefiningJob job;

    public RefiningStartedEvent(Entity entity, RefiningJob job) {
        this.entity = entity;
        this.job = job;
    }
}
```

`RefiningCompletedEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

import java.util.Map;

public final class RefiningCompletedEvent {
    public final Entity entity;
    public final RefiningJob job;
    public final Map<String, Integer> producedMaterials;

    public RefiningCompletedEvent(Entity entity, RefiningJob job,
                                  Map<String, Integer> producedMaterials) {
        this.entity = entity;
        this.job = job;
        this.producedMaterials = producedMaterials;
    }
}
```

`RefiningFailedEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningFailureReason;

public final class RefiningFailedEvent {
    public final Entity entity;
    public final RefiningFailureReason reason;

    public RefiningFailedEvent(Entity entity, RefiningFailureReason reason) {
        this.entity = entity;
        this.reason = reason;
    }
}
```

`RefiningCancelledEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

import java.util.Map;

public final class RefiningCancelledEvent {
    public final Entity entity;
    public final RefiningJob job;
    public final Map<String, Integer> returnedInputs;

    public RefiningCancelledEvent(Entity entity, RefiningJob job,
                                  Map<String, Integer> returnedInputs) {
        this.entity = entity;
        this.job = job;
        this.returnedInputs = returnedInputs;
    }
}
```

`RefiningQueueChangedEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

import java.util.List;

public final class RefiningQueueChangedEvent {
    public final Entity entity;
    public final List<RefiningJob> queue;

    public RefiningQueueChangedEvent(Entity entity, List<RefiningJob> queue) {
        this.entity = entity;
        this.queue = List.copyOf(queue);
    }
}
```

`RefiningPausedEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

public final class RefiningPausedEvent {
    public final Entity entity;
    public final RefiningJob job;

    public RefiningPausedEvent(Entity entity, RefiningJob job) {
        this.entity = entity;
        this.job = job;
    }
}
```

`RefiningResumedEvent.java`:
```java
package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

public final class RefiningResumedEvent {
    public final Entity entity;
    public final RefiningJob job;

    public RefiningResumedEvent(Entity entity, RefiningJob job) {
        this.entity = entity;
        this.job = job;
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningJobTest" --info`
Expected: All 9 tests PASS.

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/RefiningJobState.java \
  core/src/main/java/com/galacticodyssey/crafting/RefiningJob.java \
  core/src/main/java/com/galacticodyssey/crafting/RefiningFailureReason.java \
  core/src/main/java/com/galacticodyssey/crafting/events/ \
  core/src/test/java/com/galacticodyssey/crafting/RefiningJobTest.java
git commit -m "feat(crafting): add RefiningJob, job state machine, and all refining events"
```

---

## Task 6: RefineryComponent and Snapshots

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/components/RefineryComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/RefiningJobSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/RefinerySnapshot.java`

- [ ] **Step 1: Create RefiningJobSnapshot**

```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefiningJobSnapshot {
    public String jobId;
    public String recipeId;
    public String state;
    public float progress;
    public float totalTime;
    public Map<String, Integer> inputsConsumed = new HashMap<>();
    public List<OutputEntry> outputs = new ArrayList<>();

    public RefiningJobSnapshot() {}

    public static class OutputEntry {
        public String materialId;
        public int quantity;

        public OutputEntry() {}

        public OutputEntry(String materialId, int quantity) {
            this.materialId = materialId;
            this.quantity = quantity;
        }
    }
}
```

- [ ] **Step 2: Create RefinerySnapshot**

```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class RefinerySnapshot {
    public int tier;
    public int maxQueueSize;
    public float speedMultiplier;
    public float powerCostPerSecond;
    public List<RefiningJobSnapshot> jobs = new ArrayList<>();

    public RefinerySnapshot() {}
}
```

- [ ] **Step 3: Create RefineryComponent**

```java
package com.galacticodyssey.crafting.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.crafting.RefiningJob;
import com.galacticodyssey.crafting.RefiningJobState;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.RefiningJobSnapshot;
import com.galacticodyssey.persistence.snapshots.RefinerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RefineryComponent implements Component, Snapshotable<RefinerySnapshot> {
    private final int tier;
    private final int maxQueueSize;
    private final float speedMultiplier;
    private final float powerCostPerSecond;
    private final List<RefiningJob> jobQueue = new ArrayList<>();

    public RefineryComponent(int tier, int maxQueueSize,
                             float speedMultiplier, float powerCostPerSecond) {
        this.tier = tier;
        this.maxQueueSize = maxQueueSize;
        this.speedMultiplier = speedMultiplier;
        this.powerCostPerSecond = powerCostPerSecond;
    }

    public RefiningJob getActiveJob() {
        return jobQueue.isEmpty() ? null : jobQueue.get(0);
    }

    public boolean isQueueFull() {
        return jobQueue.size() >= maxQueueSize;
    }

    public boolean canProcessRecipe(RefiningRecipe recipe) {
        return recipe.requiredTier <= tier;
    }

    public void addJob(RefiningJob job) {
        jobQueue.add(job);
    }

    public void removeJob(RefiningJob job) {
        jobQueue.remove(job);
    }

    public List<RefiningJob> getJobQueue() {
        return Collections.unmodifiableList(jobQueue);
    }

    public int getTier() { return tier; }
    public int getMaxQueueSize() { return maxQueueSize; }
    public float getSpeedMultiplier() { return speedMultiplier; }
    public float getPowerCostPerSecond() { return powerCostPerSecond; }

    @Override
    public RefinerySnapshot takeSnapshot() {
        RefinerySnapshot snap = new RefinerySnapshot();
        snap.tier = tier;
        snap.maxQueueSize = maxQueueSize;
        snap.speedMultiplier = speedMultiplier;
        snap.powerCostPerSecond = powerCostPerSecond;
        for (RefiningJob job : jobQueue) {
            RefiningJobSnapshot js = new RefiningJobSnapshot();
            js.jobId = job.getJobId();
            js.recipeId = job.getRecipeId();
            js.state = job.getState().name();
            js.progress = job.getProgress();
            js.totalTime = job.getTotalTime();
            js.inputsConsumed.putAll(job.getInputsConsumed());
            for (RefiningJob.Output out : job.getOutputs()) {
                js.outputs.add(new RefiningJobSnapshot.OutputEntry(out.materialId, out.quantity));
            }
            snap.jobs.add(js);
        }
        return snap;
    }

    @Override
    public void restoreFromSnapshot(RefinerySnapshot snap) {
        jobQueue.clear();
        for (RefiningJobSnapshot js : snap.jobs) {
            List<RefiningJob.Output> outputs = js.outputs.stream()
                .map(o -> new RefiningJob.Output(o.materialId, o.quantity))
                .collect(Collectors.toList());
            RefiningJob job = new RefiningJob(
                js.jobId, js.recipeId,
                new HashMap<>(js.inputsConsumed),
                outputs, js.totalTime,
                RefiningJobState.valueOf(js.state),
                js.progress
            );
            jobQueue.add(job);
        }
    }
}
```

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/components/RefineryComponent.java \
  core/src/main/java/com/galacticodyssey/persistence/snapshots/RefiningJobSnapshot.java \
  core/src/main/java/com/galacticodyssey/persistence/snapshots/RefinerySnapshot.java
git commit -m "feat(crafting): add RefineryComponent with job queue and snapshot persistence"
```

---

## Task 7: SkillProvider and Yield Calculation

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/systems/SkillProvider.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/systems/DefaultSkillProvider.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/RefiningConfig.java`
- Create: `core/src/main/resources/data/crafting/refining_config.json`
- Test: `core/src/test/java/com/galacticodyssey/crafting/SkillYieldBonusTest.java`

- [ ] **Step 1: Write the SkillYieldBonus test**

```java
package com.galacticodyssey.crafting;

import com.galacticodyssey.crafting.systems.SkillProvider;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillYieldBonusTest {

    private static final float YIELD_BONUS_PER_LEVEL = 0.005f;

    @Test
    void calculateYield_zeroSkill_returnsBase() {
        assertEquals(3, calculateYield(3, 0));
        assertEquals(5, calculateYield(5, 0));
    }

    @Test
    void calculateYield_level50_correctMultiplier() {
        // 1.0 + 50 * 0.005 = 1.25
        assertEquals(3, calculateYield(3, 50));  // floor(3 * 1.25) = 3
        assertEquals(6, calculateYield(5, 50));  // floor(5 * 1.25) = 6
    }

    @Test
    void calculateYield_level75_correctMultiplier() {
        // 1.0 + 75 * 0.005 = 1.375
        assertEquals(4, calculateYield(3, 75));  // floor(3 * 1.375) = 4
        assertEquals(6, calculateYield(5, 75));  // floor(5 * 1.375) = 6
    }

    @Test
    void calculateYield_level100_correctMultiplier() {
        // 1.0 + 100 * 0.005 = 1.5
        assertEquals(4, calculateYield(3, 100)); // floor(3 * 1.5) = 4
        assertEquals(7, calculateYield(5, 100)); // floor(5 * 1.5) = 7
    }

    @Test
    void calculateYield_alwaysAtLeastBase() {
        assertEquals(1, calculateYield(1, 0));
        assertEquals(1, calculateYield(1, 25));
    }

    private int calculateYield(int baseQuantity, int engineeringLevel) {
        float multiplier = 1.0f + engineeringLevel * YIELD_BONUS_PER_LEVEL;
        return Math.max(baseQuantity, (int) Math.floor(baseQuantity * multiplier));
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.SkillYieldBonusTest" --info`
Expected: All 5 tests PASS. (This test validates the formula only — no external deps.)

- [ ] **Step 3: Create SkillProvider interface**

```java
package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.Entity;

public interface SkillProvider {
    int getSkillLevel(Entity entity, String skillName);
}
```

- [ ] **Step 4: Create DefaultSkillProvider**

```java
package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.Entity;

public class DefaultSkillProvider implements SkillProvider {
    @Override
    public int getSkillLevel(Entity entity, String skillName) {
        return 0;
    }
}
```

- [ ] **Step 5: Create RefiningConfig**

```java
package com.galacticodyssey.crafting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

public class RefiningConfig {
    public float yieldBonusPerLevel = 0.005f;
    public String yieldSkillName = "engineering";

    public RefiningConfig() {}

    public RefiningConfig(float yieldBonusPerLevel, String yieldSkillName) {
        this.yieldBonusPerLevel = yieldBonusPerLevel;
        this.yieldSkillName = yieldSkillName;
    }

    public int calculateYield(int baseQuantity, int skillLevel) {
        float multiplier = 1.0f + skillLevel * yieldBonusPerLevel;
        return Math.max(baseQuantity, (int) Math.floor(baseQuantity * multiplier));
    }

    public static RefiningConfig loadFromFile() {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/refining_config.json"));
        RefiningConfig config = new RefiningConfig();
        config.yieldBonusPerLevel = root.getFloat("yieldBonusPerLevel", 0.005f);
        config.yieldSkillName = root.getString("yieldSkillName", "engineering");
        return config;
    }
}
```

- [ ] **Step 6: Create refining_config.json**

Create `core/src/main/resources/data/crafting/refining_config.json`:

```json
{
  "yieldBonusPerLevel": 0.005,
  "yieldSkillName": "engineering"
}
```

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/systems/SkillProvider.java \
  core/src/main/java/com/galacticodyssey/crafting/systems/DefaultSkillProvider.java \
  core/src/main/java/com/galacticodyssey/crafting/RefiningConfig.java \
  core/src/main/resources/data/crafting/refining_config.json \
  core/src/test/java/com/galacticodyssey/crafting/SkillYieldBonusTest.java
git commit -m "feat(crafting): add SkillProvider interface, RefiningConfig with yield formula"
```

---

## Task 8: RefiningRequestHandler

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/systems/RefiningRequestHandler.java`
- Test: `core/src/test/java/com/galacticodyssey/crafting/RefiningRequestHandlerTest.java`

- [ ] **Step 1: Write the RefiningRequestHandler test**

```java
package com.galacticodyssey.crafting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;
import com.galacticodyssey.crafting.events.RefiningFailedEvent;
import com.galacticodyssey.crafting.events.RefiningRequestEvent;
import com.galacticodyssey.crafting.events.RefiningStartedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.systems.DefaultSkillProvider;
import com.galacticodyssey.crafting.systems.RefiningRequestHandler;
import com.galacticodyssey.core.EventBus;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningRequestHandlerTest {

    private Engine engine;
    private EventBus eventBus;
    private MaterialRegistry materialRegistry;
    private RefiningRecipeRegistry recipeRegistry;
    private RefiningRequestHandler handler;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50, "", null));

        recipeRegistry = new RefiningRecipeRegistry();
        recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Process Iron Ore",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("iron_ore", 5)),
            List.of(new RecipeOutput("iron_concentrate", 3)),
            30.0f, 10f));
        recipeRegistry.register(new RefiningRecipe("refine_iron", "Refine Iron",
            RecipeCategory.REFINEMENT, 2,
            List.of(new RecipeInput("iron_concentrate", 4)),
            List.of(new RecipeOutput("iron_ingot", 2)),
            60.0f, 20f));

        RefiningConfig config = new RefiningConfig(0.005f, "engineering");
        handler = new RefiningRequestHandler(eventBus, recipeRegistry, materialRegistry,
            config, new DefaultSkillProvider());
        engine.addSystem(handler);
    }

    private Entity createRefineryEntity(int tier, int queueSize) {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(tier, queueSize, 1.0f, 10f));
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        entity.add(storage);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    void validRequest_consumesInputsAndQueuesJob() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 10);

        AtomicReference<RefiningStartedEvent> started = new AtomicReference<>();
        eventBus.subscribe(RefiningStartedEvent.class, started::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(started.get());
        assertEquals(5, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
        assertEquals(1, entity.getComponent(RefineryComponent.class).getJobQueue().size());
    }

    @Test
    void noRefinery_firesFailedEvent() {
        Entity entity = new Entity();
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        entity.add(storage);
        engine.addEntity(entity);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.NO_REFINERY, failed.get().reason);
    }

    @Test
    void tierTooLow_firesFailedEvent() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_concentrate", 10);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "refine_iron"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.TIER_TOO_LOW, failed.get().reason);
    }

    @Test
    void insufficientMaterials_firesFailedEvent() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 2);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.INSUFFICIENT_MATERIALS, failed.get().reason);
        assertEquals(2, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
    }

    @Test
    void queueFull_firesFailedEvent() {
        Entity entity = createRefineryEntity(1, 1);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 20);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.QUEUE_FULL, failed.get().reason);
    }

    @Test
    void unknownRecipe_firesFailedEvent() {
        Entity entity = createRefineryEntity(3, 4);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "nonexistent_recipe"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.RECIPE_NOT_FOUND, failed.get().reason);
    }

    @Test
    void batchCount_queuesMultipleJobs() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 15);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore", 3));
        engine.update(0.016f);

        assertEquals(3, entity.getComponent(RefineryComponent.class).getJobQueue().size());
        assertEquals(0, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
    }

    @Test
    void batchCount_partialBatch_queuesAsManyAsPossible() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 7);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore", 3));
        engine.update(0.016f);

        assertEquals(1, entity.getComponent(RefineryComponent.class).getJobQueue().size());
        assertEquals(2, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
        assertNotNull(failed.get());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningRequestHandlerTest" --info`
Expected: Compilation failure — RefiningRequestHandler doesn't exist.

- [ ] **Step 3: Create RefiningRequestHandler**

```java
package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.crafting.RecipeOutput;
import com.galacticodyssey.crafting.RefiningConfig;
import com.galacticodyssey.crafting.RefiningFailureReason;
import com.galacticodyssey.crafting.RefiningJob;
import com.galacticodyssey.crafting.RefiningJobState;
import com.galacticodyssey.crafting.RecipeInput;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.events.RefiningFailedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.events.RefiningRequestEvent;
import com.galacticodyssey.crafting.events.RefiningStartedEvent;
import com.galacticodyssey.core.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefiningRequestHandler extends EntitySystem {
    private static final int PRIORITY = 4;

    private final EventBus eventBus;
    private final RefiningRecipeRegistry recipeRegistry;
    private final MaterialRegistry materialRegistry;
    private final RefiningConfig config;
    private final SkillProvider skillProvider;
    private final List<RefiningRequestEvent> pendingRequests = new ArrayList<>();

    public RefiningRequestHandler(EventBus eventBus, RefiningRecipeRegistry recipeRegistry,
                                  MaterialRegistry materialRegistry, RefiningConfig config,
                                  SkillProvider skillProvider) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.recipeRegistry = recipeRegistry;
        this.materialRegistry = materialRegistry;
        this.config = config;
        this.skillProvider = skillProvider;
        eventBus.subscribe(RefiningRequestEvent.class, pendingRequests::add);
    }

    @Override
    public void update(float deltaTime) {
        for (RefiningRequestEvent request : pendingRequests) {
            processRequest(request);
        }
        pendingRequests.clear();
    }

    private void processRequest(RefiningRequestEvent request) {
        RefineryComponent refinery = request.entity.getComponent(RefineryComponent.class);
        if (refinery == null) {
            eventBus.publish(new RefiningFailedEvent(request.entity, RefiningFailureReason.NO_REFINERY));
            return;
        }

        RefiningRecipe recipe = recipeRegistry.getRecipe(request.recipeId);
        if (recipe == null) {
            eventBus.publish(new RefiningFailedEvent(request.entity, RefiningFailureReason.RECIPE_NOT_FOUND));
            return;
        }

        if (!refinery.canProcessRecipe(recipe)) {
            eventBus.publish(new RefiningFailedEvent(request.entity, RefiningFailureReason.TIER_TOO_LOW));
            return;
        }

        MaterialStorageComponent storage = request.entity.getComponent(MaterialStorageComponent.class);
        int queued = 0;

        for (int i = 0; i < request.batchCount; i++) {
            if (refinery.isQueueFull()) {
                eventBus.publish(new RefiningFailedEvent(request.entity, RefiningFailureReason.QUEUE_FULL));
                break;
            }
            if (!hasInputs(storage, recipe)) {
                eventBus.publish(new RefiningFailedEvent(request.entity, RefiningFailureReason.INSUFFICIENT_MATERIALS));
                break;
            }

            Map<String, Integer> consumed = consumeInputs(storage, recipe);
            int engineeringLevel = skillProvider.getSkillLevel(request.entity, config.yieldSkillName);
            List<RefiningJob.Output> outputs = new ArrayList<>();
            for (RecipeOutput out : recipe.outputs) {
                int yield = config.calculateYield(out.baseQuantity, engineeringLevel);
                outputs.add(new RefiningJob.Output(out.materialId, yield));
            }

            float totalTime = recipe.processingTime / refinery.getSpeedMultiplier();
            RefiningJob job = new RefiningJob(recipe.recipeId, consumed, outputs, totalTime);

            if (refinery.getActiveJob() == null) {
                job.setState(RefiningJobState.ACTIVE);
            }
            refinery.addJob(job);
            eventBus.publish(new RefiningStartedEvent(request.entity, job));
            queued++;
        }

        if (queued > 0) {
            eventBus.publish(new RefiningQueueChangedEvent(request.entity, refinery.getJobQueue()));
        }
    }

    private boolean hasInputs(MaterialStorageComponent storage, RefiningRecipe recipe) {
        for (RecipeInput input : recipe.inputs) {
            if (!storage.hasEnough(input.materialId, input.quantity)) return false;
        }
        return true;
    }

    private Map<String, Integer> consumeInputs(MaterialStorageComponent storage, RefiningRecipe recipe) {
        Map<String, Integer> consumed = new HashMap<>();
        for (RecipeInput input : recipe.inputs) {
            storage.tryConsume(input.materialId, input.quantity);
            consumed.put(input.materialId, input.quantity);
        }
        return consumed;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningRequestHandlerTest" --info`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/systems/RefiningRequestHandler.java \
  core/src/test/java/com/galacticodyssey/crafting/RefiningRequestHandlerTest.java
git commit -m "feat(crafting): add RefiningRequestHandler with validation and batch queuing"
```

---

## Task 9: RefiningSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/systems/RefiningSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/crafting/RefiningSystemTest.java`

- [ ] **Step 1: Write the RefiningSystem test**

```java
package com.galacticodyssey.crafting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.events.RefiningCompletedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.systems.RefiningSystem;
import com.galacticodyssey.core.EventBus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private MaterialRegistry materialRegistry;
    private RefiningSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));

        system = new RefiningSystem(eventBus);
        engine.addSystem(system);
    }

    private Entity createEntityWithActiveJob(float totalTime) {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(1, 4, 1.0f, 10f);
        RefiningJob job = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            totalTime);
        job.setState(RefiningJobState.ACTIVE);
        refinery.addJob(job);
        entity.add(refinery);
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);
        return entity;
    }

    @Test
    void update_advancesActiveJobProgress() {
        Entity entity = createEntityWithActiveJob(10.0f);
        engine.update(2.5f);

        RefiningJob job = entity.getComponent(RefineryComponent.class).getActiveJob();
        assertEquals(0.25f, job.getProgress(), 0.01f);
    }

    @Test
    void update_jobCompletesAtFullProgress() {
        Entity entity = createEntityWithActiveJob(1.0f);

        AtomicReference<RefiningCompletedEvent> completed = new AtomicReference<>();
        eventBus.subscribe(RefiningCompletedEvent.class, completed::set);

        engine.update(1.0f);

        assertNotNull(completed.get());
        assertEquals(3, completed.get().producedMaterials.get("iron_concentrate"));
    }

    @Test
    void update_completedJob_addsMaterialsToStorage() {
        Entity entity = createEntityWithActiveJob(1.0f);
        engine.update(1.0f);

        MaterialStorageComponent storage = entity.getComponent(MaterialStorageComponent.class);
        assertEquals(3, storage.getQuantity("iron_concentrate"));
    }

    @Test
    void update_completedJob_removedFromQueue() {
        Entity entity = createEntityWithActiveJob(1.0f);
        engine.update(1.0f);

        RefineryComponent refinery = entity.getComponent(RefineryComponent.class);
        assertTrue(refinery.getJobQueue().isEmpty());
    }

    @Test
    void update_nextJobActivatesAfterCompletion() {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(1, 4, 1.0f, 10f);

        RefiningJob job1 = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            1.0f);
        job1.setState(RefiningJobState.ACTIVE);

        RefiningJob job2 = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            2.0f);
        job2.setState(RefiningJobState.QUEUED);

        refinery.addJob(job1);
        refinery.addJob(job2);
        entity.add(refinery);
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);

        engine.update(1.0f);

        RefiningJob activeJob = refinery.getActiveJob();
        assertNotNull(activeJob);
        assertEquals(RefiningJobState.ACTIVE, activeJob.getState());
        assertSame(job2, activeJob);
    }

    @Test
    void update_noActiveJob_doesNothing() {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(1, 4, 1.0f, 10f));
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);

        assertDoesNotThrow(() -> engine.update(1.0f));
    }

    @Test
    void update_speedMultiplier_affectsProgress() {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(2, 4, 2.0f, 20f);
        RefiningJob job = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            10.0f);
        job.setState(RefiningJobState.ACTIVE);
        refinery.addJob(job);
        entity.add(refinery);
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);

        // totalTime = 10, speedMultiplier = 2.0, deltaTime = 2.5
        // progress = (2.5 * 2.0) / 10.0 = 0.5
        engine.update(2.5f);
        assertEquals(0.5f, job.getProgress(), 0.01f);
    }

    @Test
    void update_multipleTicksToComplete() {
        Entity entity = createEntityWithActiveJob(4.0f);

        engine.update(1.0f); // 0.25
        engine.update(1.0f); // 0.50
        engine.update(1.0f); // 0.75

        RefiningJob job = entity.getComponent(RefineryComponent.class).getActiveJob();
        assertNotNull(job);
        assertEquals(0.75f, job.getProgress(), 0.01f);

        AtomicReference<RefiningCompletedEvent> completed = new AtomicReference<>();
        eventBus.subscribe(RefiningCompletedEvent.class, completed::set);

        engine.update(1.0f); // 1.0 -> complete
        assertNotNull(completed.get());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningSystemTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Create RefiningSystem**

```java
package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.crafting.RefiningJob;
import com.galacticodyssey.crafting.RefiningJobState;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.events.RefiningCompletedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.events.RefiningPausedEvent;
import com.galacticodyssey.crafting.events.RefiningResumedEvent;
import com.galacticodyssey.core.EventBus;

import java.util.HashMap;
import java.util.Map;

public class RefiningSystem extends IteratingSystem {
    private static final int PRIORITY = 5;

    private static final ComponentMapper<RefineryComponent> refineryMapper =
        ComponentMapper.getFor(RefineryComponent.class);
    private static final ComponentMapper<MaterialStorageComponent> storageMapper =
        ComponentMapper.getFor(MaterialStorageComponent.class);

    private final EventBus eventBus;

    public RefiningSystem(EventBus eventBus) {
        super(Family.all(RefineryComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        RefineryComponent refinery = refineryMapper.get(entity);
        RefiningJob activeJob = refinery.getActiveJob();
        if (activeJob == null) return;
        if (activeJob.getState() == RefiningJobState.QUEUED) {
            activeJob.setState(RefiningJobState.ACTIVE);
        }

        float progressDelta = (deltaTime * refinery.getSpeedMultiplier()) / activeJob.getTotalTime();
        activeJob.advanceProgress(progressDelta);

        if (activeJob.isComplete()) {
            completeJob(entity, refinery, activeJob);
        }
    }

    private void completeJob(Entity entity, RefineryComponent refinery, RefiningJob job) {
        MaterialStorageComponent storage = storageMapper.get(entity);
        Map<String, Integer> produced = new HashMap<>();

        for (RefiningJob.Output output : job.getOutputs()) {
            if (storage != null) {
                storage.tryAdd(output.materialId, output.quantity);
            }
            produced.put(output.materialId, output.quantity);
        }

        job.setState(RefiningJobState.COMPLETE);
        refinery.removeJob(job);
        eventBus.publish(new RefiningCompletedEvent(entity, job, produced));
        eventBus.publish(new RefiningQueueChangedEvent(entity, refinery.getJobQueue()));

        RefiningJob nextJob = refinery.getActiveJob();
        if (nextJob != null && nextJob.getState() == RefiningJobState.QUEUED) {
            nextJob.setState(RefiningJobState.ACTIVE);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.RefiningSystemTest" --info`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/systems/RefiningSystem.java \
  core/src/test/java/com/galacticodyssey/crafting/RefiningSystemTest.java
git commit -m "feat(crafting): add RefiningSystem that advances queued jobs and produces materials"
```

---

## Task 10: Refinery Module Definitions

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/crafting/data/RefineryModuleDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/crafting/data/RefineryModuleRegistry.java`
- Create: `core/src/main/resources/data/crafting/refinery_modules.json`

- [ ] **Step 1: Create RefineryModuleDefinition**

```java
package com.galacticodyssey.crafting.data;

public class RefineryModuleDefinition {
    public final String moduleId;
    public final String name;
    public final int tier;
    public final int maxQueueSize;
    public final float speedMultiplier;
    public final float powerDraw;
    public final float weight;
    public final int cost;

    public RefineryModuleDefinition(String moduleId, String name, int tier,
                                    int maxQueueSize, float speedMultiplier,
                                    float powerDraw, float weight, int cost) {
        this.moduleId = moduleId;
        this.name = name;
        this.tier = tier;
        this.maxQueueSize = maxQueueSize;
        this.speedMultiplier = speedMultiplier;
        this.powerDraw = powerDraw;
        this.weight = weight;
        this.cost = cost;
    }

    public RefineryModuleDefinition() {
        this("", "", 1, 2, 1.0f, 10f, 500f, 5000);
    }
}
```

- [ ] **Step 2: Create RefineryModuleRegistry**

```java
package com.galacticodyssey.crafting.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefineryModuleRegistry {
    private final Map<String, RefineryModuleDefinition> byId = new HashMap<>();

    public void register(RefineryModuleDefinition definition) {
        if (byId.containsKey(definition.moduleId)) {
            throw new IllegalArgumentException("Duplicate module ID: " + definition.moduleId);
        }
        byId.put(definition.moduleId, definition);
    }

    public RefineryModuleDefinition get(String moduleId) {
        return byId.get(moduleId);
    }

    public List<RefineryModuleDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    public void loadFromFile() {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/refinery_modules.json"));
        JsonValue modules = root.get("refineryModules");
        for (JsonValue entry = modules.child; entry != null; entry = entry.next) {
            register(new RefineryModuleDefinition(
                entry.getString("moduleId"),
                entry.getString("name"),
                entry.getInt("tier"),
                entry.getInt("maxQueueSize"),
                entry.getFloat("speedMultiplier"),
                entry.getFloat("powerDraw"),
                entry.getFloat("weight"),
                entry.getInt("cost")
            ));
        }
    }
}
```

- [ ] **Step 3: Create refinery_modules.json**

Create `core/src/main/resources/data/crafting/refinery_modules.json`:

```json
{
  "refineryModules": [
    {
      "moduleId": "refinery_basic",
      "name": "Basic Ship Refinery",
      "tier": 1,
      "maxQueueSize": 2,
      "speedMultiplier": 1.0,
      "powerDraw": 10,
      "weight": 500,
      "cost": 5000
    },
    {
      "moduleId": "refinery_advanced",
      "name": "Advanced Ship Refinery",
      "tier": 2,
      "maxQueueSize": 4,
      "speedMultiplier": 1.5,
      "powerDraw": 20,
      "weight": 800,
      "cost": 25000
    },
    {
      "moduleId": "refinery_industrial",
      "name": "Industrial Ship Refinery",
      "tier": 3,
      "maxQueueSize": 6,
      "speedMultiplier": 2.0,
      "powerDraw": 35,
      "weight": 1200,
      "cost": 100000
    }
  ]
}
```

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/crafting/data/RefineryModuleDefinition.java \
  core/src/main/java/com/galacticodyssey/crafting/data/RefineryModuleRegistry.java \
  core/src/main/resources/data/crafting/refinery_modules.json
git commit -m "feat(crafting): add refinery module definitions with tier/speed/cost data"
```

---

## Task 11: Integration Test

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/crafting/RefiningPipelineIntegrationTest.java`

- [ ] **Step 1: Write the full pipeline integration test**

```java
package com.galacticodyssey.crafting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;
import com.galacticodyssey.crafting.events.RefiningCompletedEvent;
import com.galacticodyssey.crafting.events.RefiningRequestEvent;
import com.galacticodyssey.crafting.events.RefiningStartedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.systems.DefaultSkillProvider;
import com.galacticodyssey.crafting.systems.RefiningRequestHandler;
import com.galacticodyssey.crafting.systems.RefiningSystem;
import com.galacticodyssey.core.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningPipelineIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private MaterialRegistry materialRegistry;
    private RefiningRecipeRegistry recipeRegistry;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50, "", null));

        recipeRegistry = new RefiningRecipeRegistry();
        recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Process Iron Ore",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("iron_ore", 5)),
            List.of(new RecipeOutput("iron_concentrate", 3)),
            10.0f, 10f));
        recipeRegistry.register(new RefiningRecipe("refine_iron", "Refine Iron",
            RecipeCategory.REFINEMENT, 2,
            List.of(new RecipeInput("iron_concentrate", 4)),
            List.of(new RecipeOutput("iron_ingot", 2)),
            10.0f, 20f));

        RefiningConfig config = new RefiningConfig(0.005f, "engineering");

        engine.addSystem(new RefiningRequestHandler(eventBus, recipeRegistry,
            materialRegistry, config, new DefaultSkillProvider()));
        engine.addSystem(new RefiningSystem(eventBus));
    }

    @Test
    void fullPipeline_rawOreToRefinedIngot() {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(2, 4, 1.0f, 10f));
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        storage.tryAdd("iron_ore", 10);
        entity.add(storage);
        engine.addEntity(entity);

        List<RefiningCompletedEvent> completions = new ArrayList<>();
        eventBus.subscribe(RefiningCompletedEvent.class, completions::add);

        // Step 1: Request processing of iron ore
        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f); // handler processes request

        assertEquals(5, storage.getQuantity("iron_ore")); // 5 consumed
        assertEquals(1, entity.getComponent(RefineryComponent.class).getJobQueue().size());

        // Step 2: Tick to completion (10 seconds at 1x speed)
        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(1, completions.size());
        assertEquals(3, storage.getQuantity("iron_concentrate"));

        // Step 3: Now refine the concentrate into ingots
        // Need 4 concentrate but only have 3 — process more ore first
        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(2, completions.size());
        assertEquals(6, storage.getQuantity("iron_concentrate")); // 3 + 3

        // Step 4: Refine iron concentrate → iron ingot
        eventBus.publish(new RefiningRequestEvent(entity, "refine_iron"));
        engine.update(0.016f);

        assertEquals(2, storage.getQuantity("iron_concentrate")); // 6 - 4 consumed

        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(3, completions.size());
        assertEquals(2, storage.getQuantity("iron_ingot"));
    }

    @Test
    void eventSequence_correctOrder() {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(1, 4, 1.0f, 10f));
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        storage.tryAdd("iron_ore", 5);
        entity.add(storage);
        engine.addEntity(entity);

        List<String> eventLog = new ArrayList<>();
        eventBus.subscribe(RefiningStartedEvent.class, e -> eventLog.add("STARTED"));
        eventBus.subscribe(RefiningQueueChangedEvent.class, e -> eventLog.add("QUEUE_CHANGED"));
        eventBus.subscribe(RefiningCompletedEvent.class, e -> eventLog.add("COMPLETED"));

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertEquals(List.of("STARTED", "QUEUE_CHANGED"), eventLog);

        for (int i = 0; i < 10; i++) {
            engine.update(1.0f);
        }

        assertEquals(List.of("STARTED", "QUEUE_CHANGED", "COMPLETED", "QUEUE_CHANGED"), eventLog);
    }

    @Test
    void snapshotRoundTrip_preservesRefiningProgress() {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(1, 4, 1.0f, 10f);
        entity.add(refinery);
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        storage.tryAdd("iron_ore", 5);
        entity.add(storage);
        engine.addEntity(entity);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        // Advance to ~50%
        for (int i = 0; i < 5; i++) {
            engine.update(1.0f);
        }

        RefiningJob midJob = refinery.getActiveJob();
        assertTrue(midJob.getProgress() > 0.4f && midJob.getProgress() < 0.6f);

        // Snapshot and restore
        var refinerySnap = refinery.takeSnapshot();
        var storageSnap = storage.takeSnapshot();

        RefineryComponent restoredRefinery = new RefineryComponent(1, 4, 1.0f, 10f);
        restoredRefinery.restoreFromSnapshot(refinerySnap);

        MaterialStorageComponent restoredStorage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        restoredStorage.restoreFromSnapshot(storageSnap);

        RefiningJob restoredJob = restoredRefinery.getActiveJob();
        assertNotNull(restoredJob);
        assertEquals(midJob.getProgress(), restoredJob.getProgress(), 0.01f);
        assertEquals(midJob.getRecipeId(), restoredJob.getRecipeId());
    }
}
```

- [ ] **Step 2: Run all tests**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.crafting.*" --info`
Expected: All tests across all test files PASS.

- [ ] **Step 3: Commit**

```
git add core/src/test/java/com/galacticodyssey/crafting/RefiningPipelineIntegrationTest.java
git commit -m "test(crafting): add full pipeline integration test with snapshot round-trip"
```

---

## Summary

11 tasks, ~40 files created/modified, 9 test classes covering:
- Material model with tier/category system and snapshot persistence
- MaterialStorageComponent with weight/volume limits
- Recipe data model with tier-gated registry and cross-validation
- Time-based job queue with progress tracking and cancellation
- Event-driven architecture (8 event types) through existing EventBus
- Skill yield bonus via SkillProvider interface (decoupled from RPG system)
- Full pipeline integration: raw ore → processed → refined with save/load mid-job
- Starter content: 5 material chains, 16 materials, 11 recipes, 3 refinery tiers

**Not included (separate specs):** UI, mining/salvaging, crafting recipes, RPG skill system, ship reactor power integration.
