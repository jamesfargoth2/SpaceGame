# Economy System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully simulated supply/demand economy with local station markets and planetary production/consumption.

**Architecture:** Hybrid ECS + service. Station markets are Ashley entities with MarketComponent/PricingComponent. Planetary simulation is a plain Java service (PlanetaryEconomyManager) that pushes stock deltas via the EventBus. TransactionService validates and executes player buy/sell trades.

**Tech Stack:** Java 21, libGDX 1.13.5, Ashley 1.7.4, JUnit 5, libGDX Json for data loading

---

## File Structure

### New files to create

**Data model** (`core/src/main/java/com/galacticodyssey/economy/data/`):
- `CommodityCategory.java` — Enum: RAW_MATERIAL, REFINED_GOOD, MANUFACTURED, CONSUMABLE, LUXURY, TECHNOLOGY
- `CommodityTier.java` — Enum: COMMON, UNCOMMON, RARE, EXOTIC, ALIEN
- `CommodityDefinition.java` — POJO: id, name, category, tier, basePrice, mass, volume, tags
- `CommodityRegistry.java` — Loads commodities.json, indexes by ID/category/tier
- `MarketEntry.java` — Per-commodity stock/demand/supply data for a station
- `IndustryType.java` — Enum: MINING, AGRICULTURAL, INDUSTRIAL, HIGH_TECH, MILITARY, RESORT, OUTPOST
- `PlanetEconomyData.java` — POJO: planetId, population, industryType, productions, consumptions, childStationIds
- `PlanetEconomyRegistry.java` — Loads planet_economies.json, indexes by planet ID

**Components** (`core/src/main/java/com/galacticodyssey/economy/components/`):
- `MarketComponent.java` — Ashley component: Map of commodity ID to MarketEntry
- `PricingComponent.java` — Ashley component: Map of commodity ID to current price + stationId + volatility
- `PlayerWalletComponent.java` — Ashley component: long credits
- `CargoBayComponent.java` — Ashley component: capacity, contents map, usedVolume

**Systems** (`core/src/main/java/com/galacticodyssey/economy/systems/`):
- `PricingSystem.java` — Ashley IntervalIteratingSystem: recalculates prices, applies NPC restock
- `PlanetaryStockSystem.java` — Ashley EntitySystem: listens for planetary tick events, applies deltas

**Simulation** (`core/src/main/java/com/galacticodyssey/economy/simulation/`):
- `PricingFormula.java` — Static utility: pricing math isolated for testing
- `PlanetaryEconomyManager.java` — Service: planetary production/consumption tick

**Service** (`core/src/main/java/com/galacticodyssey/economy/service/`):
- `TransactionService.java` — Buy/sell validation and execution
- `TradeFailureReason.java` — Enum: INSUFFICIENT_FUNDS, CARGO_FULL, INSUFFICIENT_STOCK, COMMODITY_NOT_IN_CARGO

**Events** (`core/src/main/java/com/galacticodyssey/economy/events/`):
- `TradeCompletedEvent.java` — stationId, commodityId, quantity, unitPrice, totalPrice, isBuy
- `TradeFailedEvent.java` — reason, commodityId, quantity
- `CargoChangedEvent.java` — shipEntityId (int)
- `WalletChangedEvent.java` — playerId (int), newBalance
- `ProductionTickEvent.java` — planetId, deltas map
- `ShortageEvent.java` — planetId, commodityId, deficit
- `SurplusEvent.java` — planetId, commodityId, excess

**Data files** (`core/src/main/resources/data/economy/`):
- `commodities.json` — 25 commodity definitions
- `planet_economies.json` — Planet economy profiles

**Tests** (`core/src/test/java/com/galacticodyssey/economy/`):
- `data/CommodityRegistryTest.java`
- `simulation/PricingFormulaTest.java`
- `service/TransactionServiceTest.java`
- `systems/PricingSystemTest.java`
- `data/PlanetEconomyRegistryTest.java`
- `simulation/PlanetaryEconomyManagerTest.java`
- `systems/PlanetaryStockSystemTest.java`
- `EconomyIntegrationTest.java`

### Existing files to modify

- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — Register economy systems, create PlanetaryEconomyManager

---

## Task 1: Commodity Data Model

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/data/CommodityCategory.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/data/CommodityTier.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/data/CommodityDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/data/MarketEntry.java`

- [ ] **Step 1: Create CommodityCategory enum**

```java
package com.galacticodyssey.economy.data;

public enum CommodityCategory {
    RAW_MATERIAL,
    REFINED_GOOD,
    MANUFACTURED,
    CONSUMABLE,
    LUXURY,
    TECHNOLOGY
}
```

- [ ] **Step 2: Create CommodityTier enum**

```java
package com.galacticodyssey.economy.data;

public enum CommodityTier {
    COMMON,
    UNCOMMON,
    RARE,
    EXOTIC,
    ALIEN
}
```

- [ ] **Step 3: Create CommodityDefinition POJO**

```java
package com.galacticodyssey.economy.data;

import java.util.HashSet;
import java.util.Set;

public class CommodityDefinition {
    public String id;
    public String name;
    public CommodityCategory category;
    public CommodityTier tier;
    public int basePrice;
    public float mass;
    public float volume;
    public Set<String> tags = new HashSet<>();
}
```

- [ ] **Step 4: Create MarketEntry**

```java
package com.galacticodyssey.economy.data;

public class MarketEntry {
    public final String commodityId;
    public int stock;
    public int maxStock;
    public float demand;
    public float supplyRate;

    public MarketEntry(String commodityId, int stock, int maxStock, float demand, float supplyRate) {
        this.commodityId = commodityId;
        this.stock = stock;
        this.maxStock = maxStock;
        this.demand = demand;
        this.supplyRate = supplyRate;
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/data/CommodityCategory.java core/src/main/java/com/galacticodyssey/economy/data/CommodityTier.java core/src/main/java/com/galacticodyssey/economy/data/CommodityDefinition.java core/src/main/java/com/galacticodyssey/economy/data/MarketEntry.java
git commit -m "feat(economy): add commodity data model enums and POJOs"
```

---

## Task 2: CommodityRegistry + JSON Data

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/data/CommodityRegistry.java`
- Create: `core/src/main/resources/data/economy/commodities.json`
- Create: `core/src/test/java/com/galacticodyssey/economy/data/CommodityRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.economy.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommodityRegistryTest {
    private CommodityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommodityRegistry();
    }

    @Test
    void registerAndGetById() {
        CommodityDefinition iron = makeCommodity("iron_ore", "Iron Ore",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 15, 2.0f, 1.5f);
        registry.register(iron);

        CommodityDefinition result = registry.get("iron_ore");
        assertNotNull(result);
        assertEquals("Iron Ore", result.name);
        assertEquals(CommodityCategory.RAW_MATERIAL, result.category);
        assertEquals(15, result.basePrice);
    }

    @Test
    void getReturnsNullForUnknownId() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void getByCategory() {
        registry.register(makeCommodity("iron_ore", "Iron Ore",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 15, 2.0f, 1.5f));
        registry.register(makeCommodity("food", "Food Rations",
                CommodityCategory.CONSUMABLE, CommodityTier.COMMON, 20, 1.0f, 1.0f));
        registry.register(makeCommodity("copper", "Copper",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 12, 2.5f, 1.5f));

        List<CommodityDefinition> rawMaterials = registry.getByCategory(CommodityCategory.RAW_MATERIAL);
        assertEquals(2, rawMaterials.size());
        assertTrue(rawMaterials.stream().allMatch(c -> c.category == CommodityCategory.RAW_MATERIAL));
    }

    @Test
    void getByTier() {
        registry.register(makeCommodity("iron_ore", "Iron Ore",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 15, 2.0f, 1.5f));
        registry.register(makeCommodity("quantum_foam", "Quantum Foam",
                CommodityCategory.TECHNOLOGY, CommodityTier.EXOTIC, 10000, 0.1f, 0.1f));

        List<CommodityDefinition> common = registry.getByTier(CommodityTier.COMMON);
        assertEquals(1, common.size());
        assertEquals("iron_ore", common.get(0).id);
    }

    @Test
    void getAllReturnsAllRegistered() {
        registry.register(makeCommodity("a", "A", CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 10, 1f, 1f));
        registry.register(makeCommodity("b", "B", CommodityCategory.LUXURY, CommodityTier.RARE, 500, 1f, 1f));

        List<CommodityDefinition> all = registry.getAll();
        assertEquals(2, all.size());
    }

    private CommodityDefinition makeCommodity(String id, String name, CommodityCategory category,
                                               CommodityTier tier, int basePrice, float mass, float volume) {
        CommodityDefinition def = new CommodityDefinition();
        def.id = id;
        def.name = name;
        def.category = category;
        def.tier = tier;
        def.basePrice = basePrice;
        def.mass = mass;
        def.volume = volume;
        return def;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.data.CommodityRegistryTest" --info`
Expected: FAIL — `CommodityRegistry` class does not exist

- [ ] **Step 3: Write CommodityRegistry**

```java
package com.galacticodyssey.economy.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommodityRegistry {
    private final Map<String, CommodityDefinition> byId = new HashMap<>();

    public void register(CommodityDefinition definition) {
        byId.put(definition.id, definition);
    }

    public CommodityDefinition get(String id) {
        return byId.get(id);
    }

    public List<CommodityDefinition> getByCategory(CommodityCategory category) {
        List<CommodityDefinition> result = new ArrayList<>();
        for (CommodityDefinition def : byId.values()) {
            if (def.category == category) {
                result.add(def);
            }
        }
        return result;
    }

    public List<CommodityDefinition> getByTier(CommodityTier tier) {
        List<CommodityDefinition> result = new ArrayList<>();
        for (CommodityDefinition def : byId.values()) {
            if (def.tier == tier) {
                result.add(def);
            }
        }
        return result;
    }

    public List<CommodityDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    public void loadFromFiles() {
        Json json = new Json();
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/economy/commodities.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            CommodityDefinition def = json.readValue(CommodityDefinition.class, entry);
            register(def);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.data.CommodityRegistryTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 5: Create commodities.json**

Create `core/src/main/resources/data/economy/commodities.json`:

```json
[
  {"id": "iron_ore", "name": "Iron Ore", "category": "RAW_MATERIAL", "tier": "COMMON", "basePrice": 15, "mass": 2.0, "volume": 1.5, "tags": []},
  {"id": "copper", "name": "Copper", "category": "RAW_MATERIAL", "tier": "COMMON", "basePrice": 12, "mass": 2.5, "volume": 1.5, "tags": []},
  {"id": "silicon", "name": "Silicon", "category": "RAW_MATERIAL", "tier": "COMMON", "basePrice": 18, "mass": 1.8, "volume": 1.0, "tags": []},
  {"id": "carbon", "name": "Carbon", "category": "RAW_MATERIAL", "tier": "COMMON", "basePrice": 8, "mass": 1.2, "volume": 1.0, "tags": []},
  {"id": "water", "name": "Water", "category": "CONSUMABLE", "tier": "COMMON", "basePrice": 10, "mass": 1.0, "volume": 1.0, "tags": []},
  {"id": "food_rations", "name": "Food Rations", "category": "CONSUMABLE", "tier": "COMMON", "basePrice": 25, "mass": 0.8, "volume": 0.8, "tags": []},

  {"id": "titanium", "name": "Titanium", "category": "RAW_MATERIAL", "tier": "UNCOMMON", "basePrice": 85, "mass": 2.2, "volume": 1.2, "tags": []},
  {"id": "lithium_cells", "name": "Lithium Cells", "category": "REFINED_GOOD", "tier": "UNCOMMON", "basePrice": 120, "mass": 1.5, "volume": 0.8, "tags": []},
  {"id": "tungsten_alloy", "name": "Tungsten Alloy", "category": "REFINED_GOOD", "tier": "UNCOMMON", "basePrice": 150, "mass": 3.0, "volume": 1.0, "tags": []},
  {"id": "medical_supplies", "name": "Medical Supplies", "category": "CONSUMABLE", "tier": "UNCOMMON", "basePrice": 95, "mass": 0.5, "volume": 0.6, "tags": []},
  {"id": "synthetic_textiles", "name": "Synthetic Textiles", "category": "MANUFACTURED", "tier": "UNCOMMON", "basePrice": 75, "mass": 0.3, "volume": 1.2, "tags": []},
  {"id": "manufactured_parts", "name": "Manufactured Parts", "category": "MANUFACTURED", "tier": "UNCOMMON", "basePrice": 110, "mass": 1.8, "volume": 1.0, "tags": []},

  {"id": "iridium_ingots", "name": "Iridium Ingots", "category": "REFINED_GOOD", "tier": "RARE", "basePrice": 750, "mass": 3.5, "volume": 0.5, "tags": []},
  {"id": "neutronium", "name": "Neutronium", "category": "RAW_MATERIAL", "tier": "RARE", "basePrice": 1200, "mass": 8.0, "volume": 0.3, "tags": []},
  {"id": "dark_crystals", "name": "Dark Crystals", "category": "RAW_MATERIAL", "tier": "RARE", "basePrice": 900, "mass": 0.5, "volume": 0.4, "tags": []},
  {"id": "military_electronics", "name": "Military-Grade Electronics", "category": "TECHNOLOGY", "tier": "RARE", "basePrice": 1500, "mass": 0.8, "volume": 0.6, "tags": []},
  {"id": "luxury_goods", "name": "Luxury Goods", "category": "LUXURY", "tier": "RARE", "basePrice": 600, "mass": 0.4, "volume": 0.8, "tags": []},

  {"id": "zero_point_cells", "name": "Zero-Point Cells", "category": "TECHNOLOGY", "tier": "EXOTIC", "basePrice": 8000, "mass": 0.2, "volume": 0.2, "tags": []},
  {"id": "quantum_foam", "name": "Quantum Foam", "category": "TECHNOLOGY", "tier": "EXOTIC", "basePrice": 12000, "mass": 0.1, "volume": 0.1, "tags": []},
  {"id": "void_essence", "name": "Void Essence", "category": "RAW_MATERIAL", "tier": "EXOTIC", "basePrice": 6000, "mass": 0.05, "volume": 0.3, "tags": []},
  {"id": "salvaged_components", "name": "Salvaged Components", "category": "MANUFACTURED", "tier": "EXOTIC", "basePrice": 5000, "mass": 1.5, "volume": 1.0, "tags": []},

  {"id": "bio_polymers", "name": "Bio-Polymers", "category": "RAW_MATERIAL", "tier": "ALIEN", "basePrice": 3500, "mass": 0.6, "volume": 0.5, "tags": []},
  {"id": "xeno_tech_fragments", "name": "Xeno-Tech Fragments", "category": "TECHNOLOGY", "tier": "ALIEN", "basePrice": 7500, "mass": 0.3, "volume": 0.2, "tags": []},
  {"id": "psionic_resonators", "name": "Psionic Resonators", "category": "TECHNOLOGY", "tier": "ALIEN", "basePrice": 10000, "mass": 0.2, "volume": 0.15, "tags": []}
]
```

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/data/CommodityRegistry.java core/src/main/resources/data/economy/commodities.json core/src/test/java/com/galacticodyssey/economy/data/CommodityRegistryTest.java
git commit -m "feat(economy): add CommodityRegistry with JSON data and tests"
```

---

## Task 3: PricingFormula

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/simulation/PricingFormula.java`
- Create: `core/src/test/java/com/galacticodyssey/economy/simulation/PricingFormulaTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.economy.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingFormulaTest {

    @Test
    void normalSupplyDemandBalance() {
        // stock == demand → demandMultiplier = 1.0, no volatility
        int price = PricingFormula.calculate(100, 50, 50f, 0f);
        assertEquals(100, price);
    }

    @Test
    void lowStockDrivesUpPrice() {
        // stock=10, demand=50 → multiplier = 50/10 = 5.0 (capped at 5.0)
        int price = PricingFormula.calculate(100, 10, 50f, 0f);
        assertEquals(500, price);
    }

    @Test
    void highStockDrivesDownPrice() {
        // stock=500, demand=50 → multiplier = 50/500 = 0.1, clamped to 0.2
        int price = PricingFormula.calculate(100, 500, 50f, 0f);
        assertEquals(20, price);
    }

    @Test
    void zeroStockUsesMaxMultiplier() {
        // stock=0 → demand / max(0,1) = 50/1 = 50, clamped to 5.0
        int price = PricingFormula.calculate(100, 0, 50f, 0f);
        assertEquals(500, price);
    }

    @Test
    void zeroDemandUsesMinMultiplier() {
        // demand=0 → 0/50 = 0, clamped to 0.2
        int price = PricingFormula.calculate(100, 50, 0f, 0f);
        assertEquals(20, price);
    }

    @Test
    void positiveVolatilityIncreasesPrice() {
        // volatility = +0.1 → price * 1.1
        int price = PricingFormula.calculate(100, 50, 50f, 0.1f);
        assertEquals(110, price);
    }

    @Test
    void negativeVolatilityDecreasesPrice() {
        // volatility = -0.1 → price * 0.9
        int price = PricingFormula.calculate(100, 50, 50f, -0.1f);
        assertEquals(90, price);
    }

    @Test
    void priceNeverBelowOne() {
        // Very extreme case: basePrice=1, high stock, negative volatility
        int price = PricingFormula.calculate(1, 1000, 1f, -0.1f);
        assertTrue(price >= 1, "Price should never drop below 1 credit");
    }

    @Test
    void demandMultiplierClampedAt5() {
        // stock=1, demand=1000 → raw multiplier=1000, clamped to 5.0
        int price = PricingFormula.calculate(100, 1, 1000f, 0f);
        assertEquals(500, price);
    }

    @Test
    void demandMultiplierClampedAtPointTwo() {
        // stock=10000, demand=1 → raw multiplier=0.0001, clamped to 0.2
        int price = PricingFormula.calculate(100, 10000, 1f, 0f);
        assertEquals(20, price);
    }

    @Test
    void volatilityFromSeed() {
        float v1 = PricingFormula.volatilityForStation("station_alpha");
        float v2 = PricingFormula.volatilityForStation("station_beta");
        float v1Again = PricingFormula.volatilityForStation("station_alpha");

        assertEquals(v1, v1Again, "Same station ID should produce same volatility");
        assertTrue(v1 >= -0.1f && v1 <= 0.1f, "Volatility should be in [-0.1, 0.1]");
        assertTrue(v2 >= -0.1f && v2 <= 0.1f, "Volatility should be in [-0.1, 0.1]");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.simulation.PricingFormulaTest" --info`
Expected: FAIL — `PricingFormula` class does not exist

- [ ] **Step 3: Write PricingFormula**

```java
package com.galacticodyssey.economy.simulation;

public final class PricingFormula {

    private static final float MIN_MULTIPLIER = 0.2f;
    private static final float MAX_MULTIPLIER = 5.0f;

    private PricingFormula() {}

    public static int calculate(int basePrice, int stock, float demand, float volatility) {
        float safeDivisor = Math.max(stock, 1);
        float rawMultiplier = demand / safeDivisor;
        float demandMultiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, rawMultiplier));
        float price = basePrice * demandMultiplier * (1f + volatility);
        return Math.max(1, Math.round(price));
    }

    public static float volatilityForStation(String stationId) {
        int hash = stationId.hashCode();
        float normalized = (hash & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        return (normalized * 0.2f) - 0.1f;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.simulation.PricingFormulaTest" --info`
Expected: All 11 tests PASS

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/simulation/PricingFormula.java core/src/test/java/com/galacticodyssey/economy/simulation/PricingFormulaTest.java
git commit -m "feat(economy): add PricingFormula with supply/demand curve and tests"
```

---

## Task 4: Economy Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/components/MarketComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/components/PricingComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/components/PlayerWalletComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/components/CargoBayComponent.java`

- [ ] **Step 1: Create MarketComponent**

```java
package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.economy.data.MarketEntry;

import java.util.HashMap;
import java.util.Map;

public class MarketComponent implements Component {
    public String stationId;
    public final Map<String, MarketEntry> entries = new HashMap<>();
}
```

- [ ] **Step 2: Create PricingComponent**

```java
package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;

import java.util.HashMap;
import java.util.Map;

public class PricingComponent implements Component {
    public final Map<String, Integer> prices = new HashMap<>();
    public float volatility;
}
```

- [ ] **Step 3: Create PlayerWalletComponent**

```java
package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;

public class PlayerWalletComponent implements Component {
    public long credits;
}
```

- [ ] **Step 4: Create CargoBayComponent**

```java
package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;

import java.util.HashMap;
import java.util.Map;

public class CargoBayComponent implements Component {
    public float capacity;
    public final Map<String, Integer> contents = new HashMap<>();
    public float usedVolume;
}
```

- [ ] **Step 5: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/components/MarketComponent.java core/src/main/java/com/galacticodyssey/economy/components/PricingComponent.java core/src/main/java/com/galacticodyssey/economy/components/PlayerWalletComponent.java core/src/main/java/com/galacticodyssey/economy/components/CargoBayComponent.java
git commit -m "feat(economy): add Ashley components for market, pricing, wallet, and cargo"
```

---

## Task 5: Economy Events and TradeFailureReason

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/service/TradeFailureReason.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/events/TradeCompletedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/events/TradeFailedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/events/CargoChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/events/WalletChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/events/ProductionTickEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/events/ShortageEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/events/SurplusEvent.java`

- [ ] **Step 1: Create TradeFailureReason enum**

```java
package com.galacticodyssey.economy.service;

public enum TradeFailureReason {
    INSUFFICIENT_FUNDS,
    CARGO_FULL,
    INSUFFICIENT_STOCK,
    COMMODITY_NOT_IN_CARGO
}
```

- [ ] **Step 2: Create TradeCompletedEvent**

```java
package com.galacticodyssey.economy.events;

public final class TradeCompletedEvent {
    public final String stationId;
    public final String commodityId;
    public final int quantity;
    public final int unitPrice;
    public final long totalPrice;
    public final boolean isBuy;

    public TradeCompletedEvent(String stationId, String commodityId, int quantity,
                                int unitPrice, long totalPrice, boolean isBuy) {
        this.stationId = stationId;
        this.commodityId = commodityId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.isBuy = isBuy;
    }
}
```

- [ ] **Step 3: Create TradeFailedEvent**

```java
package com.galacticodyssey.economy.events;

import com.galacticodyssey.economy.service.TradeFailureReason;

public final class TradeFailedEvent {
    public final TradeFailureReason reason;
    public final String commodityId;
    public final int quantity;

    public TradeFailedEvent(TradeFailureReason reason, String commodityId, int quantity) {
        this.reason = reason;
        this.commodityId = commodityId;
        this.quantity = quantity;
    }
}
```

- [ ] **Step 4: Create CargoChangedEvent**

```java
package com.galacticodyssey.economy.events;

public final class CargoChangedEvent {
    public final int shipEntityId;

    public CargoChangedEvent(int shipEntityId) {
        this.shipEntityId = shipEntityId;
    }
}
```

- [ ] **Step 5: Create WalletChangedEvent**

```java
package com.galacticodyssey.economy.events;

public final class WalletChangedEvent {
    public final int playerEntityId;
    public final long newBalance;

    public WalletChangedEvent(int playerEntityId, long newBalance) {
        this.playerEntityId = playerEntityId;
        this.newBalance = newBalance;
    }
}
```

- [ ] **Step 6: Create ProductionTickEvent**

```java
package com.galacticodyssey.economy.events;

import java.util.Map;

public final class ProductionTickEvent {
    public final String planetId;
    public final Map<String, Map<String, Integer>> stationDeltas;

    public ProductionTickEvent(String planetId, Map<String, Map<String, Integer>> stationDeltas) {
        this.planetId = planetId;
        this.stationDeltas = stationDeltas;
    }
}
```

- [ ] **Step 7: Create ShortageEvent**

```java
package com.galacticodyssey.economy.events;

public final class ShortageEvent {
    public final String planetId;
    public final String commodityId;
    public final int deficit;

    public ShortageEvent(String planetId, String commodityId, int deficit) {
        this.planetId = planetId;
        this.commodityId = commodityId;
        this.deficit = deficit;
    }
}
```

- [ ] **Step 8: Create SurplusEvent**

```java
package com.galacticodyssey.economy.events;

public final class SurplusEvent {
    public final String planetId;
    public final String commodityId;
    public final int excess;

    public SurplusEvent(String planetId, String commodityId, int excess) {
        this.planetId = planetId;
        this.commodityId = commodityId;
        this.excess = excess;
    }
}
```

- [ ] **Step 9: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/events/ core/src/main/java/com/galacticodyssey/economy/service/TradeFailureReason.java
git commit -m "feat(economy): add economy events and TradeFailureReason enum"
```

---

## Task 6: TransactionService

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java`
- Create: `core/src/test/java/com/galacticodyssey/economy/service/TransactionServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.economy.service;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityCategory;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.CommodityTier;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.CargoChangedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.economy.events.TradeFailedEvent;
import com.galacticodyssey.economy.events.WalletChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    private EventBus eventBus;
    private CommodityRegistry commodityRegistry;
    private TransactionService service;

    private Entity station;
    private Entity player;
    private Entity ship;

    private MarketComponent market;
    private PricingComponent pricing;
    private PlayerWalletComponent wallet;
    private CargoBayComponent cargo;

    private final List<TradeCompletedEvent> completedEvents = new ArrayList<>();
    private final List<TradeFailedEvent> failedEvents = new ArrayList<>();
    private final List<WalletChangedEvent> walletEvents = new ArrayList<>();
    private final List<CargoChangedEvent> cargoEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        commodityRegistry = new CommodityRegistry();

        CommodityDefinition iron = new CommodityDefinition();
        iron.id = "iron_ore";
        iron.name = "Iron Ore";
        iron.category = CommodityCategory.RAW_MATERIAL;
        iron.tier = CommodityTier.COMMON;
        iron.basePrice = 15;
        iron.mass = 2.0f;
        iron.volume = 1.5f;
        commodityRegistry.register(iron);

        service = new TransactionService(commodityRegistry, eventBus);

        station = new Entity();
        market = new MarketComponent();
        market.stationId = "station_1";
        market.entries.put("iron_ore", new MarketEntry("iron_ore", 100, 200, 50f, 5f));
        station.add(market);
        pricing = new PricingComponent();
        pricing.prices.put("iron_ore", 15);
        station.add(pricing);

        player = new Entity();
        wallet = new PlayerWalletComponent();
        wallet.credits = 1000;
        player.add(wallet);

        ship = new Entity();
        cargo = new CargoBayComponent();
        cargo.capacity = 100f;
        cargo.usedVolume = 0f;
        ship.add(cargo);

        eventBus.subscribe(TradeCompletedEvent.class, completedEvents::add);
        eventBus.subscribe(TradeFailedEvent.class, failedEvents::add);
        eventBus.subscribe(WalletChangedEvent.class, walletEvents::add);
        eventBus.subscribe(CargoChangedEvent.class, cargoEvents::add);
    }

    @Test
    void buyDeductsCreditsAndTransfersStock() {
        service.buy(station, player, ship, "iron_ore", 10);

        assertEquals(850, wallet.credits);
        assertEquals(90, market.entries.get("iron_ore").stock);
        assertEquals(10, cargo.contents.getOrDefault("iron_ore", 0));
        assertEquals(15.0f, cargo.usedVolume, 0.01f);
    }

    @Test
    void buyPublishesEvents() {
        service.buy(station, player, ship, "iron_ore", 5);

        assertEquals(1, completedEvents.size());
        TradeCompletedEvent evt = completedEvents.get(0);
        assertEquals("station_1", evt.stationId);
        assertEquals("iron_ore", evt.commodityId);
        assertEquals(5, evt.quantity);
        assertEquals(15, evt.unitPrice);
        assertEquals(75, evt.totalPrice);
        assertTrue(evt.isBuy);

        assertEquals(1, walletEvents.size());
        assertEquals(925, walletEvents.get(0).newBalance);

        assertEquals(1, cargoEvents.size());
    }

    @Test
    void buyFailsWithInsufficientFunds() {
        wallet.credits = 10;

        service.buy(station, player, ship, "iron_ore", 10);

        assertEquals(10, wallet.credits);
        assertEquals(100, market.entries.get("iron_ore").stock);
        assertEquals(0, completedEvents.size());
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.INSUFFICIENT_FUNDS, failedEvents.get(0).reason);
    }

    @Test
    void buyFailsWithInsufficientStock() {
        service.buy(station, player, ship, "iron_ore", 200);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.INSUFFICIENT_STOCK, failedEvents.get(0).reason);
    }

    @Test
    void buyFailsWithCargoFull() {
        cargo.capacity = 1.0f;

        service.buy(station, player, ship, "iron_ore", 10);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.CARGO_FULL, failedEvents.get(0).reason);
    }

    @Test
    void sellAddsCreditsAndTransfersStock() {
        cargo.contents.put("iron_ore", 20);
        cargo.usedVolume = 30.0f;

        service.sell(station, player, ship, "iron_ore", 10);

        assertEquals(1150, wallet.credits);
        assertEquals(110, market.entries.get("iron_ore").stock);
        assertEquals(10, cargo.contents.get("iron_ore"));
        assertEquals(15.0f, cargo.usedVolume, 0.01f);
    }

    @Test
    void sellPublishesEvents() {
        cargo.contents.put("iron_ore", 5);
        cargo.usedVolume = 7.5f;

        service.sell(station, player, ship, "iron_ore", 5);

        assertEquals(1, completedEvents.size());
        assertFalse(completedEvents.get(0).isBuy);
        assertEquals(75, completedEvents.get(0).totalPrice);

        assertEquals(1, walletEvents.size());
        assertEquals(1, cargoEvents.size());
    }

    @Test
    void sellFailsWhenCommodityNotInCargo() {
        service.sell(station, player, ship, "iron_ore", 5);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.COMMODITY_NOT_IN_CARGO, failedEvents.get(0).reason);
    }

    @Test
    void sellFailsWhenNotEnoughInCargo() {
        cargo.contents.put("iron_ore", 3);
        cargo.usedVolume = 4.5f;

        service.sell(station, player, ship, "iron_ore", 10);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.COMMODITY_NOT_IN_CARGO, failedEvents.get(0).reason);
    }

    @Test
    void sellRemovesCommodityEntryWhenFullyDepleted() {
        cargo.contents.put("iron_ore", 5);
        cargo.usedVolume = 7.5f;

        service.sell(station, player, ship, "iron_ore", 5);

        assertFalse(cargo.contents.containsKey("iron_ore"));
        assertEquals(0f, cargo.usedVolume, 0.01f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.service.TransactionServiceTest" --info`
Expected: FAIL — `TransactionService` class does not exist

- [ ] **Step 3: Write TransactionService**

```java
package com.galacticodyssey.economy.service;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.CargoChangedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.economy.events.TradeFailedEvent;
import com.galacticodyssey.economy.events.WalletChangedEvent;

public class TransactionService {
    private static final ComponentMapper<MarketComponent> MARKET_M = ComponentMapper.getFor(MarketComponent.class);
    private static final ComponentMapper<PricingComponent> PRICING_M = ComponentMapper.getFor(PricingComponent.class);
    private static final ComponentMapper<PlayerWalletComponent> WALLET_M = ComponentMapper.getFor(PlayerWalletComponent.class);
    private static final ComponentMapper<CargoBayComponent> CARGO_M = ComponentMapper.getFor(CargoBayComponent.class);

    private final CommodityRegistry commodityRegistry;
    private final EventBus eventBus;

    public TransactionService(CommodityRegistry commodityRegistry, EventBus eventBus) {
        this.commodityRegistry = commodityRegistry;
        this.eventBus = eventBus;
    }

    public void buy(Entity station, Entity player, Entity ship, String commodityId, int quantity) {
        MarketComponent market = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);
        PlayerWalletComponent wallet = WALLET_M.get(player);
        CargoBayComponent cargo = CARGO_M.get(ship);
        CommodityDefinition commodity = commodityRegistry.get(commodityId);

        MarketEntry entry = market.entries.get(commodityId);
        int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);
        long totalPrice = (long) unitPrice * quantity;

        if (entry.stock < quantity) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.INSUFFICIENT_STOCK, commodityId, quantity));
            return;
        }
        if (wallet.credits < totalPrice) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.INSUFFICIENT_FUNDS, commodityId, quantity));
            return;
        }
        float requiredVolume = commodity.volume * quantity;
        if (cargo.usedVolume + requiredVolume > cargo.capacity) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.CARGO_FULL, commodityId, quantity));
            return;
        }

        wallet.credits -= totalPrice;
        entry.stock -= quantity;
        cargo.contents.merge(commodityId, quantity, Integer::sum);
        cargo.usedVolume += requiredVolume;

        eventBus.publish(new TradeCompletedEvent(market.stationId, commodityId, quantity, unitPrice, totalPrice, true));
        eventBus.publish(new WalletChangedEvent(player.hashCode(), wallet.credits));
        eventBus.publish(new CargoChangedEvent(ship.hashCode()));
    }

    public void sell(Entity station, Entity player, Entity ship, String commodityId, int quantity) {
        MarketComponent market = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);
        PlayerWalletComponent wallet = WALLET_M.get(player);
        CargoBayComponent cargo = CARGO_M.get(ship);
        CommodityDefinition commodity = commodityRegistry.get(commodityId);

        int inCargo = cargo.contents.getOrDefault(commodityId, 0);
        if (inCargo < quantity) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.COMMODITY_NOT_IN_CARGO, commodityId, quantity));
            return;
        }

        int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);
        long totalPrice = (long) unitPrice * quantity;

        MarketEntry entry = market.entries.get(commodityId);
        entry.stock += quantity;

        int remaining = inCargo - quantity;
        if (remaining <= 0) {
            cargo.contents.remove(commodityId);
        } else {
            cargo.contents.put(commodityId, remaining);
        }
        cargo.usedVolume -= commodity.volume * quantity;

        wallet.credits += totalPrice;

        eventBus.publish(new TradeCompletedEvent(market.stationId, commodityId, quantity, unitPrice, totalPrice, false));
        eventBus.publish(new WalletChangedEvent(player.hashCode(), wallet.credits));
        eventBus.publish(new CargoChangedEvent(ship.hashCode()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.service.TransactionServiceTest" --info`
Expected: All 10 tests PASS

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java core/src/test/java/com/galacticodyssey/economy/service/TransactionServiceTest.java
git commit -m "feat(economy): add TransactionService for buy/sell trades with tests"
```

---

## Task 7: PricingSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/systems/PricingSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/economy/systems/PricingSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityCategory;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.CommodityTier;
import com.galacticodyssey.economy.data.MarketEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingSystemTest {
    private Engine engine;
    private CommodityRegistry commodityRegistry;

    @BeforeEach
    void setUp() {
        commodityRegistry = new CommodityRegistry();

        CommodityDefinition iron = new CommodityDefinition();
        iron.id = "iron_ore";
        iron.name = "Iron Ore";
        iron.category = CommodityCategory.RAW_MATERIAL;
        iron.tier = CommodityTier.COMMON;
        iron.basePrice = 100;
        iron.mass = 2.0f;
        iron.volume = 1.5f;
        commodityRegistry.register(iron);

        engine = new Engine();
        PricingSystem system = new PricingSystem(commodityRegistry, 1.0f);
        engine.addSystem(system);
    }

    private Entity createStation(String stationId, int stock, int maxStock, float demand, float supplyRate) {
        Entity station = new Entity();
        MarketComponent market = new MarketComponent();
        market.stationId = stationId;
        market.entries.put("iron_ore", new MarketEntry("iron_ore", stock, maxStock, demand, supplyRate));
        station.add(market);

        PricingComponent pricing = new PricingComponent();
        pricing.volatility = 0f;
        station.add(pricing);

        engine.addEntity(station);
        return station;
    }

    @Test
    void recalculatesPricesOnTick() {
        Entity station = createStation("test_station", 50, 200, 50f, 0f);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertEquals(100, pricing.prices.get("iron_ore"));
    }

    @Test
    void lowStockIncreasesPrice() {
        Entity station = createStation("test_station", 10, 200, 50f, 0f);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertEquals(500, pricing.prices.get("iron_ore"));
    }

    @Test
    void highStockDecreasesPrice() {
        Entity station = createStation("test_station", 500, 500, 50f, 0f);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertEquals(20, pricing.prices.get("iron_ore"));
    }

    @Test
    void appliesNpcRestock() {
        Entity station = createStation("test_station", 50, 200, 50f, 10f);

        engine.update(1.0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(60, market.entries.get("iron_ore").stock);
    }

    @Test
    void restockCapsAtMaxStock() {
        Entity station = createStation("test_station", 195, 200, 50f, 10f);

        engine.update(1.0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(200, market.entries.get("iron_ore").stock);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.systems.PricingSystemTest" --info`
Expected: FAIL — `PricingSystem` class does not exist

- [ ] **Step 3: Write PricingSystem**

```java
package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IntervalIteratingSystem;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.simulation.PricingFormula;

public class PricingSystem extends IntervalIteratingSystem {
    public static final int PRIORITY = 50;

    private static final ComponentMapper<MarketComponent> MARKET_M = ComponentMapper.getFor(MarketComponent.class);
    private static final ComponentMapper<PricingComponent> PRICING_M = ComponentMapper.getFor(PricingComponent.class);

    private final CommodityRegistry commodityRegistry;

    public PricingSystem(CommodityRegistry commodityRegistry, float interval) {
        super(Family.all(MarketComponent.class, PricingComponent.class).get(), interval, PRIORITY);
        this.commodityRegistry = commodityRegistry;
    }

    @Override
    protected void processEntity(Entity entity) {
        MarketComponent market = MARKET_M.get(entity);
        PricingComponent pricing = PRICING_M.get(entity);

        for (MarketEntry entry : market.entries.values()) {
            entry.stock = Math.min(entry.stock + (int) entry.supplyRate, entry.maxStock);

            CommodityDefinition commodity = commodityRegistry.get(entry.commodityId);
            if (commodity != null) {
                int price = PricingFormula.calculate(
                        commodity.basePrice, entry.stock, entry.demand, pricing.volatility);
                pricing.prices.put(entry.commodityId, price);
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.systems.PricingSystemTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/systems/PricingSystem.java core/src/test/java/com/galacticodyssey/economy/systems/PricingSystemTest.java
git commit -m "feat(economy): add PricingSystem with interval-based price recalculation and tests"
```

---

## Task 8: Planetary Data Model

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/data/IndustryType.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/data/PlanetEconomyData.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/data/PlanetEconomyRegistry.java`
- Create: `core/src/main/resources/data/economy/planet_economies.json`
- Create: `core/src/test/java/com/galacticodyssey/economy/data/PlanetEconomyRegistryTest.java`

- [ ] **Step 1: Create IndustryType enum**

```java
package com.galacticodyssey.economy.data;

public enum IndustryType {
    MINING,
    AGRICULTURAL,
    INDUSTRIAL,
    HIGH_TECH,
    MILITARY,
    RESORT,
    OUTPOST
}
```

- [ ] **Step 2: Create PlanetEconomyData**

```java
package com.galacticodyssey.economy.data;

import java.util.ArrayList;
import java.util.List;

public class PlanetEconomyData {
    public String planetId;
    public long population;
    public IndustryType industryType;
    public List<ProductionEntry> productions = new ArrayList<>();
    public List<ConsumptionEntry> consumptions = new ArrayList<>();
    public List<String> childStationIds = new ArrayList<>();

    public static class ProductionEntry {
        public String commodityId;
        public int outputPerTick;
    }

    public static class ConsumptionEntry {
        public String commodityId;
        public int demandPerTick;
    }
}
```

- [ ] **Step 3: Write the failing test**

```java
package com.galacticodyssey.economy.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetEconomyRegistryTest {
    private PlanetEconomyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PlanetEconomyRegistry();
    }

    @Test
    void registerAndGetByPlanetId() {
        PlanetEconomyData data = makePlanet("planet_1", IndustryType.MINING, 50000);
        registry.register(data);

        PlanetEconomyData result = registry.get("planet_1");
        assertNotNull(result);
        assertEquals(IndustryType.MINING, result.industryType);
        assertEquals(50000, result.population);
    }

    @Test
    void getReturnsNullForUnknown() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void getAllReturnsCopy() {
        registry.register(makePlanet("planet_1", IndustryType.MINING, 50000));
        registry.register(makePlanet("planet_2", IndustryType.AGRICULTURAL, 200000));

        assertEquals(2, registry.getAll().size());
    }

    @Test
    void registerWithProductionsAndConsumptions() {
        PlanetEconomyData data = makePlanet("planet_1", IndustryType.MINING, 50000);

        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 20;
        data.productions.add(prod);

        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 10;
        data.consumptions.add(cons);

        data.childStationIds.add("station_1");

        registry.register(data);

        PlanetEconomyData result = registry.get("planet_1");
        assertEquals(1, result.productions.size());
        assertEquals("iron_ore", result.productions.get(0).commodityId);
        assertEquals(20, result.productions.get(0).outputPerTick);
        assertEquals(1, result.consumptions.size());
        assertEquals(1, result.childStationIds.size());
    }

    private PlanetEconomyData makePlanet(String id, IndustryType type, long population) {
        PlanetEconomyData data = new PlanetEconomyData();
        data.planetId = id;
        data.industryType = type;
        data.population = population;
        return data;
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.data.PlanetEconomyRegistryTest" --info`
Expected: FAIL — `PlanetEconomyRegistry` class does not exist

- [ ] **Step 5: Write PlanetEconomyRegistry**

```java
package com.galacticodyssey.economy.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanetEconomyRegistry {
    private final Map<String, PlanetEconomyData> byPlanetId = new HashMap<>();

    public void register(PlanetEconomyData data) {
        byPlanetId.put(data.planetId, data);
    }

    public PlanetEconomyData get(String planetId) {
        return byPlanetId.get(planetId);
    }

    public List<PlanetEconomyData> getAll() {
        return new ArrayList<>(byPlanetId.values());
    }

    public void loadFromFiles() {
        Json json = new Json();
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/economy/planet_economies.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            PlanetEconomyData data = json.readValue(PlanetEconomyData.class, entry);
            register(data);
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.data.PlanetEconomyRegistryTest" --info`
Expected: All 4 tests PASS

- [ ] **Step 7: Create planet_economies.json**

Create `core/src/main/resources/data/economy/planet_economies.json`:

```json
[
  {
    "planetId": "kepler_prime",
    "population": 500000,
    "industryType": "MINING",
    "productions": [
      {"commodityId": "iron_ore", "outputPerTick": 25},
      {"commodityId": "copper", "outputPerTick": 20},
      {"commodityId": "titanium", "outputPerTick": 8}
    ],
    "consumptions": [
      {"commodityId": "food_rations", "demandPerTick": 15},
      {"commodityId": "water", "demandPerTick": 12},
      {"commodityId": "medical_supplies", "demandPerTick": 5}
    ],
    "childStationIds": ["kepler_station_alpha", "kepler_station_beta"]
  },
  {
    "planetId": "eden_verde",
    "population": 300000,
    "industryType": "AGRICULTURAL",
    "productions": [
      {"commodityId": "food_rations", "outputPerTick": 30},
      {"commodityId": "water", "outputPerTick": 25},
      {"commodityId": "bio_polymers", "outputPerTick": 5}
    ],
    "consumptions": [
      {"commodityId": "silicon", "demandPerTick": 8},
      {"commodityId": "manufactured_parts", "demandPerTick": 6}
    ],
    "childStationIds": ["eden_orbital"]
  },
  {
    "planetId": "nova_foundry",
    "population": 800000,
    "industryType": "INDUSTRIAL",
    "productions": [
      {"commodityId": "tungsten_alloy", "outputPerTick": 15},
      {"commodityId": "synthetic_textiles", "outputPerTick": 12},
      {"commodityId": "military_electronics", "outputPerTick": 6},
      {"commodityId": "manufactured_parts", "outputPerTick": 20}
    ],
    "consumptions": [
      {"commodityId": "iron_ore", "demandPerTick": 20},
      {"commodityId": "copper", "demandPerTick": 15},
      {"commodityId": "titanium", "demandPerTick": 10},
      {"commodityId": "lithium_cells", "demandPerTick": 8}
    ],
    "childStationIds": ["nova_station_1", "nova_station_2", "nova_station_3"]
  },
  {
    "planetId": "zenith_labs",
    "population": 150000,
    "industryType": "HIGH_TECH",
    "productions": [
      {"commodityId": "zero_point_cells", "outputPerTick": 2},
      {"commodityId": "quantum_foam", "outputPerTick": 1},
      {"commodityId": "psionic_resonators", "outputPerTick": 1}
    ],
    "consumptions": [
      {"commodityId": "iridium_ingots", "demandPerTick": 4},
      {"commodityId": "manufactured_parts", "demandPerTick": 10},
      {"commodityId": "dark_crystals", "demandPerTick": 3}
    ],
    "childStationIds": ["zenith_orbital"]
  },
  {
    "planetId": "fort_bastion",
    "population": 200000,
    "industryType": "MILITARY",
    "productions": [],
    "consumptions": [
      {"commodityId": "military_electronics", "demandPerTick": 12},
      {"commodityId": "tungsten_alloy", "demandPerTick": 8},
      {"commodityId": "food_rations", "demandPerTick": 20},
      {"commodityId": "medical_supplies", "demandPerTick": 10}
    ],
    "childStationIds": ["bastion_dockyard"]
  },
  {
    "planetId": "azure_shores",
    "population": 100000,
    "industryType": "RESORT",
    "productions": [],
    "consumptions": [
      {"commodityId": "food_rations", "demandPerTick": 18},
      {"commodityId": "water", "demandPerTick": 15},
      {"commodityId": "luxury_goods", "demandPerTick": 10},
      {"commodityId": "medical_supplies", "demandPerTick": 3}
    ],
    "childStationIds": ["azure_station"]
  },
  {
    "planetId": "outpost_sigma",
    "population": 5000,
    "industryType": "OUTPOST",
    "productions": [
      {"commodityId": "salvaged_components", "outputPerTick": 3}
    ],
    "consumptions": [
      {"commodityId": "food_rations", "demandPerTick": 3},
      {"commodityId": "water", "demandPerTick": 2},
      {"commodityId": "medical_supplies", "demandPerTick": 1},
      {"commodityId": "iron_ore", "demandPerTick": 2}
    ],
    "childStationIds": ["sigma_depot"]
  }
]
```

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/data/IndustryType.java core/src/main/java/com/galacticodyssey/economy/data/PlanetEconomyData.java core/src/main/java/com/galacticodyssey/economy/data/PlanetEconomyRegistry.java core/src/main/resources/data/economy/planet_economies.json core/src/test/java/com/galacticodyssey/economy/data/PlanetEconomyRegistryTest.java
git commit -m "feat(economy): add planetary economy data model, registry, and JSON data"
```

---

## Task 9: PlanetaryEconomyManager

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/simulation/PlanetaryEconomyManager.java`
- Create: `core/src/test/java/com/galacticodyssey/economy/simulation/PlanetaryEconomyManagerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.economy.simulation;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.data.IndustryType;
import com.galacticodyssey.economy.data.PlanetEconomyData;
import com.galacticodyssey.economy.data.PlanetEconomyRegistry;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import com.galacticodyssey.economy.events.ShortageEvent;
import com.galacticodyssey.economy.events.SurplusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanetaryEconomyManagerTest {
    private EventBus eventBus;
    private PlanetEconomyRegistry planetRegistry;
    private PlanetaryEconomyManager manager;

    private final List<ProductionTickEvent> tickEvents = new ArrayList<>();
    private final List<ShortageEvent> shortageEvents = new ArrayList<>();
    private final List<SurplusEvent> surplusEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        planetRegistry = new PlanetEconomyRegistry();

        eventBus.subscribe(ProductionTickEvent.class, tickEvents::add);
        eventBus.subscribe(ShortageEvent.class, shortageEvents::add);
        eventBus.subscribe(SurplusEvent.class, surplusEvents::add);
    }

    private PlanetEconomyData makePlanet(String id, String stationId) {
        PlanetEconomyData data = new PlanetEconomyData();
        data.planetId = id;
        data.population = 100000;
        data.industryType = IndustryType.MINING;
        data.childStationIds.add(stationId);
        return data;
    }

    @Test
    void tickProducesPositiveDeltas() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 20;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("iron_ore", 50);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("iron_ore", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, tickEvents.size());
        ProductionTickEvent evt = tickEvents.get(0);
        assertEquals("p1", evt.planetId);
        assertTrue(evt.stationDeltas.containsKey("s1"));
        assertEquals(20, evt.stationDeltas.get("s1").get("iron_ore"));
    }

    @Test
    void tickConsumesWithNegativeDeltas() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 10;
        planet.consumptions.add(cons);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("food_rations", 50);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("food_rations", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, tickEvents.size());
        assertEquals(-10, tickEvents.get(0).stationDeltas.get("s1").get("food_rations"));
    }

    @Test
    void shortageEventWhenStockTooLow() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 30;
        planet.consumptions.add(cons);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("food_rations", 10);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("food_rations", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, shortageEvents.size());
        assertEquals("food_rations", shortageEvents.get(0).commodityId);
        assertEquals(20, shortageEvents.get(0).deficit);

        assertEquals(-10, tickEvents.get(0).stationDeltas.get("s1").get("food_rations"));
    }

    @Test
    void surplusEventWhenProductionOverflows() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 50;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("iron_ore", 190);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("iron_ore", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, surplusEvents.size());
        assertEquals("iron_ore", surplusEvents.get(0).commodityId);
        assertEquals(40, surplusEvents.get(0).excess);

        assertEquals(10, tickEvents.get(0).stationDeltas.get("s1").get("iron_ore"));
    }

    @Test
    void productionDistributedAcrossMultipleStations() {
        PlanetEconomyData planet = new PlanetEconomyData();
        planet.planetId = "p1";
        planet.population = 100000;
        planet.industryType = IndustryType.MINING;
        planet.childStationIds.add("s1");
        planet.childStationIds.add("s2");

        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 30;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", new HashMap<>(Map.of("iron_ore", 50)));
        allStocks.put("s2", new HashMap<>(Map.of("iron_ore", 50)));
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", new HashMap<>(Map.of("iron_ore", 100)));
        allMaxStocks.put("s2", new HashMap<>(Map.of("iron_ore", 200)));

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        ProductionTickEvent evt = tickEvents.get(0);
        int s1Delta = evt.stationDeltas.get("s1").getOrDefault("iron_ore", 0);
        int s2Delta = evt.stationDeltas.get("s2").getOrDefault("iron_ore", 0);

        assertEquals(30, s1Delta + s2Delta);
        assertTrue(s2Delta > s1Delta, "Station with higher maxStock should get more production");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.simulation.PlanetaryEconomyManagerTest" --info`
Expected: FAIL — `PlanetaryEconomyManager` class does not exist

- [ ] **Step 3: Write PlanetaryEconomyManager**

```java
package com.galacticodyssey.economy.simulation;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.data.PlanetEconomyData;
import com.galacticodyssey.economy.data.PlanetEconomyRegistry;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import com.galacticodyssey.economy.events.ShortageEvent;
import com.galacticodyssey.economy.events.SurplusEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanetaryEconomyManager {
    private final EventBus eventBus;
    private final PlanetEconomyRegistry planetRegistry;

    public PlanetaryEconomyManager(EventBus eventBus, PlanetEconomyRegistry planetRegistry) {
        this.eventBus = eventBus;
        this.planetRegistry = planetRegistry;
    }

    public void tick(Map<String, Map<String, Integer>> stationStocks,
                     Map<String, Map<String, Integer>> stationMaxStocks) {
        for (PlanetEconomyData planet : planetRegistry.getAll()) {
            Map<String, Map<String, Integer>> stationDeltas = new HashMap<>();
            for (String stationId : planet.childStationIds) {
                stationDeltas.put(stationId, new HashMap<>());
            }

            distributeProduction(planet, stationDeltas, stationStocks, stationMaxStocks);
            distributeConsumption(planet, stationDeltas, stationStocks);

            eventBus.publish(new ProductionTickEvent(planet.planetId, stationDeltas));
        }
    }

    private void distributeProduction(PlanetEconomyData planet,
                                       Map<String, Map<String, Integer>> stationDeltas,
                                       Map<String, Map<String, Integer>> stationStocks,
                                       Map<String, Map<String, Integer>> stationMaxStocks) {
        List<String> stations = planet.childStationIds;
        if (stations.isEmpty()) return;

        for (PlanetEconomyData.ProductionEntry prod : planet.productions) {
            int totalMaxStock = 0;
            for (String stationId : stations) {
                Map<String, Integer> maxStocks = stationMaxStocks.get(stationId);
                if (maxStocks != null) {
                    totalMaxStock += maxStocks.getOrDefault(prod.commodityId, 100);
                }
            }
            if (totalMaxStock == 0) totalMaxStock = 1;

            int totalDistributed = 0;
            int totalSurplus = 0;

            for (String stationId : stations) {
                Map<String, Integer> maxStocks = stationMaxStocks.get(stationId);
                int stationMax = (maxStocks != null) ? maxStocks.getOrDefault(prod.commodityId, 100) : 100;
                float weight = (float) stationMax / totalMaxStock;
                int share = Math.round(prod.outputPerTick * weight);

                Map<String, Integer> stocks = stationStocks.get(stationId);
                int currentStock = (stocks != null) ? stocks.getOrDefault(prod.commodityId, 0) : 0;
                int available = stationMax - currentStock;
                int actualAdd = Math.min(share, available);
                int overflow = share - actualAdd;

                stationDeltas.get(stationId).merge(prod.commodityId, actualAdd, Integer::sum);
                totalDistributed += actualAdd;
                totalSurplus += overflow;
            }

            if (totalSurplus > 0) {
                eventBus.publish(new SurplusEvent(planet.planetId, prod.commodityId, totalSurplus));
            }
        }
    }

    private void distributeConsumption(PlanetEconomyData planet,
                                        Map<String, Map<String, Integer>> stationDeltas,
                                        Map<String, Map<String, Integer>> stationStocks) {
        List<String> stations = planet.childStationIds;
        if (stations.isEmpty()) return;

        for (PlanetEconomyData.ConsumptionEntry cons : planet.consumptions) {
            int demandPerStation = cons.demandPerTick / stations.size();
            int remainder = cons.demandPerTick % stations.size();
            int totalDeficit = 0;

            for (int i = 0; i < stations.size(); i++) {
                String stationId = stations.get(i);
                int stationDemand = demandPerStation + (i < remainder ? 1 : 0);

                Map<String, Integer> stocks = stationStocks.get(stationId);
                int currentStock = (stocks != null) ? stocks.getOrDefault(cons.commodityId, 0) : 0;
                int pendingDelta = stationDeltas.get(stationId).getOrDefault(cons.commodityId, 0);
                int effectiveStock = currentStock + pendingDelta;

                int actualRemove = Math.min(stationDemand, Math.max(effectiveStock, 0));
                int deficit = stationDemand - actualRemove;

                stationDeltas.get(stationId).merge(cons.commodityId, -actualRemove, Integer::sum);
                totalDeficit += deficit;
            }

            if (totalDeficit > 0) {
                eventBus.publish(new ShortageEvent(planet.planetId, cons.commodityId, totalDeficit));
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.simulation.PlanetaryEconomyManagerTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/simulation/PlanetaryEconomyManager.java core/src/test/java/com/galacticodyssey/economy/simulation/PlanetaryEconomyManagerTest.java
git commit -m "feat(economy): add PlanetaryEconomyManager with production/consumption simulation and tests"
```

---

## Task 10: PlanetaryStockSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/systems/PlanetaryStockSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/economy/systems/PlanetaryStockSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanetaryStockSystemTest {
    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        PlanetaryStockSystem system = new PlanetaryStockSystem(eventBus);
        engine.addSystem(system);
    }

    private Entity createStation(String stationId, String commodityId, int stock, int maxStock) {
        Entity station = new Entity();
        MarketComponent market = new MarketComponent();
        market.stationId = stationId;
        market.entries.put(commodityId, new MarketEntry(commodityId, stock, maxStock, 50f, 5f));
        station.add(market);

        PricingComponent pricing = new PricingComponent();
        station.add(pricing);

        engine.addEntity(station);
        return station;
    }

    @Test
    void appliesPositiveDeltaToStation() {
        Entity station = createStation("s1", "iron_ore", 50, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("iron_ore", 20)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(70, market.entries.get("iron_ore").stock);
    }

    @Test
    void appliesNegativeDeltaToStation() {
        Entity station = createStation("s1", "food_rations", 50, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("food_rations", -10)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(40, market.entries.get("food_rations").stock);
    }

    @Test
    void stockNeverGoesBelowZero() {
        Entity station = createStation("s1", "food_rations", 5, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("food_rations", -20)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(0, market.entries.get("food_rations").stock);
    }

    @Test
    void ignoresUnknownStationIds() {
        createStation("s1", "iron_ore", 50, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("unknown_station", new HashMap<>(Map.of("iron_ore", 20)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);
        // No exception thrown
    }

    @Test
    void handlesMultipleStationsAndCommodities() {
        Entity s1 = createStation("s1", "iron_ore", 50, 200);
        Entity s2 = createStation("s2", "iron_ore", 30, 200);

        s1.getComponent(MarketComponent.class).entries.put("food_rations",
                new MarketEntry("food_rations", 100, 200, 30f, 3f));

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("iron_ore", 10, "food_rations", -5)));
        deltas.put("s2", new HashMap<>(Map.of("iron_ore", 15)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        assertEquals(60, s1.getComponent(MarketComponent.class).entries.get("iron_ore").stock);
        assertEquals(95, s1.getComponent(MarketComponent.class).entries.get("food_rations").stock);
        assertEquals(45, s2.getComponent(MarketComponent.class).entries.get("iron_ore").stock);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.systems.PlanetaryStockSystemTest" --info`
Expected: FAIL — `PlanetaryStockSystem` class does not exist

- [ ] **Step 3: Write PlanetaryStockSystem**

```java
package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.ProductionTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanetaryStockSystem extends EntitySystem {
    public static final int PRIORITY = 51;

    private static final ComponentMapper<MarketComponent> MARKET_M = ComponentMapper.getFor(MarketComponent.class);

    private final Map<String, Entity> stationIndex = new HashMap<>();
    private final List<ProductionTickEvent> pendingEvents = new ArrayList<>();
    private ImmutableArray<Entity> stations;

    public PlanetaryStockSystem(EventBus eventBus) {
        super(PRIORITY);
        eventBus.subscribe(ProductionTickEvent.class, pendingEvents::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        stations = engine.getEntitiesFor(Family.all(MarketComponent.class, PricingComponent.class).get());
        rebuildIndex();
    }

    @Override
    public void update(float deltaTime) {
        if (pendingEvents.isEmpty()) return;

        rebuildIndex();

        for (ProductionTickEvent event : pendingEvents) {
            for (Map.Entry<String, Map<String, Integer>> stationEntry : event.stationDeltas.entrySet()) {
                Entity station = stationIndex.get(stationEntry.getKey());
                if (station == null) continue;

                MarketComponent market = MARKET_M.get(station);
                for (Map.Entry<String, Integer> delta : stationEntry.getValue().entrySet()) {
                    MarketEntry entry = market.entries.get(delta.getKey());
                    if (entry != null) {
                        entry.stock = Math.max(0, entry.stock + delta.getValue());
                    }
                }
            }
        }
        pendingEvents.clear();
    }

    private void rebuildIndex() {
        stationIndex.clear();
        for (Entity entity : stations) {
            MarketComponent market = MARKET_M.get(entity);
            if (market.stationId != null) {
                stationIndex.put(market.stationId, entity);
            }
        }
    }

    public Map<String, Integer> getStationStocks(String stationId) {
        Entity station = stationIndex.get(stationId);
        if (station == null) return new HashMap<>();
        MarketComponent market = MARKET_M.get(station);
        Map<String, Integer> stocks = new HashMap<>();
        for (MarketEntry entry : market.entries.values()) {
            stocks.put(entry.commodityId, entry.stock);
        }
        return stocks;
    }

    public Map<String, Integer> getStationMaxStocks(String stationId) {
        Entity station = stationIndex.get(stationId);
        if (station == null) return new HashMap<>();
        MarketComponent market = MARKET_M.get(station);
        Map<String, Integer> maxStocks = new HashMap<>();
        for (MarketEntry entry : market.entries.values()) {
            maxStocks.put(entry.commodityId, entry.maxStock);
        }
        return maxStocks;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.systems.PlanetaryStockSystemTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/systems/PlanetaryStockSystem.java core/src/test/java/com/galacticodyssey/economy/systems/PlanetaryStockSystemTest.java
git commit -m "feat(economy): add PlanetaryStockSystem to bridge planetary sim with station ECS entities"
```

---

## Task 11: Integration Tests

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/economy/EconomyIntegrationTest.java`

- [ ] **Step 1: Write integration test — full trading loop**

```java
package com.galacticodyssey.economy;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.*;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.economy.service.TransactionService;
import com.galacticodyssey.economy.simulation.PlanetaryEconomyManager;
import com.galacticodyssey.economy.systems.PlanetaryStockSystem;
import com.galacticodyssey.economy.systems.PricingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EconomyIntegrationTest {
    private Engine engine;
    private EventBus eventBus;
    private CommodityRegistry commodityRegistry;
    private TransactionService transactionService;
    private PlanetaryEconomyManager planetaryManager;
    private PlanetaryStockSystem planetaryStockSystem;

    private Entity station;
    private Entity player;
    private Entity ship;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        commodityRegistry = new CommodityRegistry();

        CommodityDefinition iron = new CommodityDefinition();
        iron.id = "iron_ore";
        iron.name = "Iron Ore";
        iron.category = CommodityCategory.RAW_MATERIAL;
        iron.tier = CommodityTier.COMMON;
        iron.basePrice = 100;
        iron.mass = 2.0f;
        iron.volume = 1.5f;
        commodityRegistry.register(iron);

        CommodityDefinition food = new CommodityDefinition();
        food.id = "food_rations";
        food.name = "Food Rations";
        food.category = CommodityCategory.CONSUMABLE;
        food.tier = CommodityTier.COMMON;
        food.basePrice = 50;
        food.mass = 0.8f;
        food.volume = 0.8f;
        commodityRegistry.register(food);

        engine = new Engine();
        PricingSystem pricingSystem = new PricingSystem(commodityRegistry, 1.0f);
        planetaryStockSystem = new PlanetaryStockSystem(eventBus);
        engine.addSystem(pricingSystem);
        engine.addSystem(planetaryStockSystem);

        transactionService = new TransactionService(commodityRegistry, eventBus);

        station = new Entity();
        MarketComponent market = new MarketComponent();
        market.stationId = "test_station";
        market.entries.put("iron_ore", new MarketEntry("iron_ore", 100, 200, 50f, 0f));
        market.entries.put("food_rations", new MarketEntry("food_rations", 80, 150, 40f, 0f));
        station.add(market);
        PricingComponent pricing = new PricingComponent();
        pricing.volatility = 0f;
        station.add(pricing);
        engine.addEntity(station);

        player = new Entity();
        PlayerWalletComponent wallet = new PlayerWalletComponent();
        wallet.credits = 50000;
        player.add(wallet);

        ship = new Entity();
        CargoBayComponent cargo = new CargoBayComponent();
        cargo.capacity = 500f;
        ship.add(cargo);
    }

    @Test
    void buyingDepletsStockAndRaisesPrice() {
        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        int priceBefore = pricing.prices.get("iron_ore");

        transactionService.buy(station, player, ship, "iron_ore", 40);

        engine.update(1.0f);

        int priceAfter = pricing.prices.get("iron_ore");
        assertTrue(priceAfter > priceBefore,
                "Price should increase after buying 40 units (stock dropped from 100 to 60)");
    }

    @Test
    void sellingIncreasesStockAndLowersPrice() {
        CargoBayComponent cargo = ship.getComponent(CargoBayComponent.class);
        cargo.contents.put("iron_ore", 50);
        cargo.usedVolume = 75f;

        engine.update(1.0f);
        PricingComponent pricing = station.getComponent(PricingComponent.class);
        int priceBefore = pricing.prices.get("iron_ore");

        transactionService.sell(station, player, ship, "iron_ore", 50);

        engine.update(1.0f);
        int priceAfter = pricing.prices.get("iron_ore");
        assertTrue(priceAfter < priceBefore,
                "Price should decrease after selling 50 units (stock rose from 100 to 150)");
    }

    @Test
    void planetaryProductionIncreasesStationStock() {
        PlanetEconomyRegistry planetRegistry = new PlanetEconomyRegistry();
        PlanetEconomyData planet = new PlanetEconomyData();
        planet.planetId = "test_planet";
        planet.population = 100000;
        planet.industryType = IndustryType.MINING;
        planet.childStationIds.add("test_station");
        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 20;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        planetaryManager = new PlanetaryEconomyManager(eventBus, planetRegistry);

        MarketComponent market = station.getComponent(MarketComponent.class);
        int stockBefore = market.entries.get("iron_ore").stock;

        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("test_station", Map.of("iron_ore", stockBefore));
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("test_station", Map.of("iron_ore", 200));

        planetaryManager.tick(allStocks, allMaxStocks);
        engine.update(0f);

        int stockAfter = market.entries.get("iron_ore").stock;
        assertEquals(stockBefore + 20, stockAfter);
    }

    @Test
    void fullCycleProduceTradeConsume() {
        PlanetEconomyRegistry planetRegistry = new PlanetEconomyRegistry();
        PlanetEconomyData planet = new PlanetEconomyData();
        planet.planetId = "test_planet";
        planet.population = 100000;
        planet.industryType = IndustryType.MINING;
        planet.childStationIds.add("test_station");

        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 15;
        planet.productions.add(prod);

        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 10;
        planet.consumptions.add(cons);
        planetRegistry.register(planet);

        planetaryManager = new PlanetaryEconomyManager(eventBus, planetRegistry);

        MarketComponent market = station.getComponent(MarketComponent.class);
        int ironBefore = market.entries.get("iron_ore").stock;
        int foodBefore = market.entries.get("food_rations").stock;

        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("test_station", Map.of("iron_ore", ironBefore, "food_rations", foodBefore));
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("test_station", Map.of("iron_ore", 200, "food_rations", 150));

        planetaryManager.tick(allStocks, allMaxStocks);
        engine.update(0f);

        assertEquals(ironBefore + 15, market.entries.get("iron_ore").stock);
        assertEquals(foodBefore - 10, market.entries.get("food_rations").stock);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertNotNull(pricing.prices.get("iron_ore"));
        assertNotNull(pricing.prices.get("food_rations"));

        List<TradeCompletedEvent> trades = new ArrayList<>();
        eventBus.subscribe(TradeCompletedEvent.class, trades::add);
        transactionService.buy(station, player, ship, "iron_ore", 10);
        assertEquals(1, trades.size());
        assertTrue(trades.get(0).isBuy);
    }

    @Test
    void twoStationsPriceDiverge() {
        CommodityDefinition food = commodityRegistry.get("food_rations");

        Entity station2 = new Entity();
        MarketComponent market2 = new MarketComponent();
        market2.stationId = "station_2";
        market2.entries.put("food_rations", new MarketEntry("food_rations", 10, 200, 80f, 0f));
        station2.add(market2);
        PricingComponent pricing2 = new PricingComponent();
        pricing2.volatility = 0f;
        station2.add(pricing2);
        engine.addEntity(station2);

        engine.update(1.0f);

        PricingComponent p1 = station.getComponent(PricingComponent.class);
        PricingComponent p2 = station2.getComponent(PricingComponent.class);

        int price1 = p1.prices.get("food_rations");
        int price2 = p2.prices.get("food_rations");

        assertTrue(price2 > price1,
                "Station with lower stock and higher demand should have higher price. " +
                "Station1=" + price1 + " Station2=" + price2);
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.EconomyIntegrationTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 3: Run full economy test suite**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.economy.*" --info`
Expected: All tests PASS across all economy test classes

- [ ] **Step 4: Commit**

```
git add core/src/test/java/com/galacticodyssey/economy/EconomyIntegrationTest.java
git commit -m "test(economy): add integration tests for full trading loop and price divergence"
```

---

## Task 12: Wire Into GameWorld

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Read current GameWorld to find system registration section**

Read `core/src/main/java/com/galacticodyssey/core/GameWorld.java` to identify where systems are registered and where to add the economy systems.

- [ ] **Step 2: Add economy system imports and registration**

Add to the imports section of GameWorld.java:

```java
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.PlanetEconomyRegistry;
import com.galacticodyssey.economy.simulation.PlanetaryEconomyManager;
import com.galacticodyssey.economy.systems.PlanetaryStockSystem;
import com.galacticodyssey.economy.systems.PricingSystem;
```

Add to the system registration section (after existing systems, before entity creation):

```java
// Economy
CommodityRegistry commodityRegistry = new CommodityRegistry();
commodityRegistry.loadFromFiles();

PlanetEconomyRegistry planetEconomyRegistry = new PlanetEconomyRegistry();
planetEconomyRegistry.loadFromFiles();

PricingSystem pricingSystem = new PricingSystem(commodityRegistry, 5.0f);
engine.addSystem(pricingSystem);

PlanetaryStockSystem planetaryStockSystem = new PlanetaryStockSystem(eventBus);
engine.addSystem(planetaryStockSystem);

PlanetaryEconomyManager planetaryEconomyManager = new PlanetaryEconomyManager(eventBus, planetEconomyRegistry);
```

Store `commodityRegistry` and `planetaryEconomyManager` as fields if other systems need them, or pass to a factory method that constructs the `TransactionService`.

- [ ] **Step 3: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

Run: `gradlew.bat :core:test --info`
Expected: All tests PASS (economy and existing tests)

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(economy): wire economy systems into GameWorld bootstrap"
```
