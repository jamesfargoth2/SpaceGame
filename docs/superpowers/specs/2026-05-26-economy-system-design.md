# Economy System Design

**Date:** 2026-05-26
**Scope:** Local + Planetary economy tiers (first increment)
**Approach:** Hybrid — ECS for local stations, plain Java service for planetary simulation

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Build order | Bottom-up (local first, then planetary) | Playable trading loop fast; upper tiers layer on later |
| Pricing model | Fully simulated supply/demand | Dynamic, responsive to player actions |
| Currency | Single universal (Credits) | Keeps complexity on the simulation, not bookkeeping |
| Smuggling | Deferred | Touches faction rep, patrol AI, stealth — too many unbuilt dependencies |
| First increment scope | Full local + planetary tier | ~20-30 commodities, station markets, planetary production/consumption |
| Architecture | Hybrid ECS + service | Stations in ECS (player-facing), planetary sim as plain service (background) |

## 1. Commodities — Data Model

Commodities are the atoms of the economy. Defined in JSON, loaded at startup.

### Data file: `data/economy/commodities.json`

Each commodity definition:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique key (e.g., `"iron_ore"`, `"quantum_foam"`) |
| `name` | String | Display name |
| `category` | Enum | `RAW_MATERIAL`, `REFINED_GOOD`, `MANUFACTURED`, `CONSUMABLE`, `LUXURY`, `TECHNOLOGY` |
| `tier` | Enum | `COMMON`, `UNCOMMON`, `RARE`, `EXOTIC`, `ALIEN` |
| `basePrice` | int | Galactic average price in credits (anchor for supply/demand curves) |
| `mass` | float | Weight per unit (affects cargo capacity) |
| `volume` | float | Space per unit (for cargo capacity limits) |
| `tags` | Set&lt;String&gt; | Extensible tags for future use (legality, faction preferences) |

### Commodity list (~25 items)

**Common (tier 1):**
- Iron Ore, Copper, Silicon, Carbon, Water, Food Rations

**Uncommon (tier 2):**
- Titanium, Lithium Cells, Tungsten Alloy, Medical Supplies, Synthetic Textiles, Manufactured Parts

**Rare (tier 3):**
- Iridium Ingots, Neutronium, Dark Crystals, Military-Grade Electronics, Luxury Goods

**Exotic (tier 4):**
- Zero-Point Cells, Quantum Foam, Void Essence, Salvaged Components

**Alien (tier 5):**
- Bio-Polymers, Xeno-Tech Fragments, Psionic Resonators

### CommodityRegistry

Loads `commodities.json` at startup. Provides lookups by ID, category, or tier. Same pattern as the existing `WeaponDataRegistry`.

**Class:** `com.galacticodyssey.economy.data.CommodityRegistry`

## 2. Local Economy — Station Markets

Each station the player can trade with is an Ashley entity with economy components.

### MarketComponent

Core market state. Contains a `Map<String, MarketEntry>` keyed by commodity ID.

**MarketEntry fields:**

| Field | Type | Description |
|-------|------|-------------|
| `commodityId` | String | Reference to commodity definition |
| `stock` | int | Current quantity available |
| `maxStock` | int | Storage capacity (soft cap, can overflow from production) |
| `demand` | float | Consumption target per tick |
| `supplyRate` | float | Passive NPC restock rate (units per tick) |

**Class:** `com.galacticodyssey.economy.components.MarketComponent`

### PricingComponent

Cached current prices, recalculated when stock changes. Separated from MarketComponent so the pricing system can run on its own schedule.

Contains a `Map<String, Integer>` of commodity ID to current price.

**Class:** `com.galacticodyssey.economy.components.PricingComponent`

### Pricing Formula

```
price = basePrice * demandMultiplier * (1 + volatility)

where:
  demandMultiplier = clamp(demand / max(stock, 1), 0.2, 5.0)
  volatility = per-station seeded random offset (range: -0.1 to +0.1)
```

- Low stock relative to demand → price spikes (up to 5x base)
- High stock relative to demand → price craters (down to 0.2x base)
- Clamp prevents absurd extremes
- Volatility seeded from station ID gives each station a consistent price personality

### PricingSystem (Ashley IntervalSystem)

**Tick interval:** ~5 real-time seconds (wall-clock, not in-game time — prices should feel responsive during play)

Responsibilities:
1. Recalculate prices for all stations with a MarketComponent using the pricing formula (round to nearest integer credit)
2. Apply passive NPC restock: `stock = min(stock + supplyRate, maxStock)` per tick
3. LOD: only fully tick stations near the player. Distant stations get a coarse catch-up calculation when the player arrives (multiply supplyRate by elapsed ticks, clamp stock).

**Class:** `com.galacticodyssey.economy.systems.PricingSystem`

### Buy/Sell Mechanics

- **Player buys** → station stock decreases, price rises on next tick
- **Player sells** → station stock increases, price falls on next tick
- Station pays the player at current price
- No bid/ask spread for now (simplicity); architecture supports adding one later
- All transactions go through `TransactionService` (see Section 4)

## 3. Planetary Economy — Production & Consumption

Background simulation that drives station stock changes independent of player action.

### PlanetaryEconomyManager

Plain Java service (not an ECS system). Ticks every 10 in-game minutes. Owned and lifecycle-managed by GameWorld.

**Class:** `com.galacticodyssey.economy.simulation.PlanetaryEconomyManager`

### PlanetEconomyData

Each planet has an economy profile. Can be defined in JSON or derived procedurally from planet properties.

**Data file:** `data/economy/planet_economies.json`

| Field | Type | Description |
|-------|------|-------------|
| `planetId` | String | Ties to galaxy system's planet identity |
| `population` | long | Drives consumption scale |
| `industryType` | Enum | `MINING`, `AGRICULTURAL`, `INDUSTRIAL`, `HIGH_TECH`, `MILITARY`, `RESORT`, `OUTPOST` |
| `productions` | List | `{commodityId, outputPerTick}` pairs |
| `consumptions` | List | `{commodityId, demandPerTick}` pairs |
| `childStationIds` | List&lt;String&gt; | Stations this planet feeds |

### Industry Type Profiles

| Industry | Produces | Consumes |
|----------|----------|----------|
| `MINING` | Iron Ore, Copper, Titanium, Neutronium | Food Rations, Medical Supplies, Water |
| `AGRICULTURAL` | Food Rations, Water, Bio-Polymers | Silicon, Manufactured Parts |
| `INDUSTRIAL` | Tungsten Alloy, Synthetic Textiles, Military-Grade Electronics, Manufactured Parts | Iron Ore, Copper, Titanium, Lithium Cells |
| `HIGH_TECH` | Zero-Point Cells, Quantum Foam, Psionic Resonators | Iridium Ingots, Manufactured Parts, Dark Crystals |
| `MILITARY` | (none — consumes heavily) | Military-Grade Electronics, Tungsten Alloy, Food Rations, Medical Supplies |
| `RESORT` | (minimal — service economy) | Food Rations, Water, Luxury Goods, Medical Supplies |
| `OUTPOST` | Salvaged Components | Food Rations, Water, Medical Supplies, Iron Ore |

### Tick Logic (every 10 in-game minutes)

1. **Produce:** Each planet generates output commodities based on its production list
2. **Distribute:** Production output distributed to child stations proportionally (weighted by station `maxStock`)
3. **Consume:** Each planet's consumption removes stock from child stations
4. **Shortage detection:** If consumption demand can't be met (stations are low), publish `ShortageEvent` — increases price pressure
5. **Surplus detection:** If production overflows station capacity, publish `SurplusEvent` — decreases price pressure

### LOD for Distant Planets

- **Near player:** Full fidelity tick every 10 in-game minutes
- **Distant:** Accumulate elapsed time. On player arrival to sector, batch catch-up: multiply production/consumption by elapsed ticks, clamp stock to reasonable bounds. Avoids simulating 10,000 planets every tick.

### PlanetaryStockSystem (Ashley System)

Listens for planetary tick events on the event bus. Applies stock deltas to station MarketComponents. This is the bridge between the plain Java service and the ECS world.

**Station resolution:** Station entities carry a string ID (e.g., in a `StationIdComponent` or as a field on MarketComponent). PlanetaryStockSystem maintains an internal `Map<String, Entity>` index built on entity addition/removal. When a `ProductionTickEvent` arrives with `childStationIds`, the system looks up entities by ID and applies deltas to their MarketComponents.

**Class:** `com.galacticodyssey.economy.systems.PlanetaryStockSystem`

## 4. Player Integration — Cargo, Wallet, Transactions

### PlayerWalletComponent

Attached to the player entity.

| Field | Type | Description |
|-------|------|-------------|
| `credits` | long | Current balance (long, not int, for large sums) |

All mutations go through `TransactionService`. No direct setter exposed.

**Class:** `com.galacticodyssey.economy.components.PlayerWalletComponent`

### CargoBayComponent

Attached to ship entities. Separate from the existing grid-based `InventoryComponent` — commodities are bulk goods, not Tetris items.

| Field | Type | Description |
|-------|------|-------------|
| `capacity` | float | Max total volume (derived from ship blueprint CARGO_BAY rooms) |
| `contents` | Map&lt;String, Integer&gt; | Commodity ID → quantity |
| `usedVolume` | float | Cached sum of `quantity * commodity.volume` |

**Class:** `com.galacticodyssey.economy.components.CargoBayComponent`

### TransactionService

Validates and executes trades atomically.

**Class:** `com.galacticodyssey.economy.service.TransactionService`

**Buy flow:**
1. Validate: player has enough credits, ship has cargo space, station has stock
2. Deduct credits from wallet
3. Remove stock from station market
4. Add commodity to ship cargo
5. Publish `TradeCompletedEvent`

**Sell flow:**
1. Validate: player has the commodity in cargo
2. Remove commodity from ship cargo
3. Add stock to station market
4. Add credits to wallet
5. Publish `TradeCompletedEvent`

**Failure:** Publish `TradeFailedEvent` with reason enum (`INSUFFICIENT_FUNDS`, `CARGO_FULL`, `INSUFFICIENT_STOCK`, `COMMODITY_NOT_IN_CARGO`).

## 5. Events

All events published on the existing `EventBus`.

| Event | Fields | Published by |
|-------|--------|-------------|
| `TradeCompletedEvent` | stationId, commodityId, quantity, unitPrice, totalPrice, isBuy | TransactionService |
| `TradeFailedEvent` | reason, commodityId, quantity | TransactionService |
| `CargoChangedEvent` | shipEntityId | TransactionService |
| `WalletChangedEvent` | playerId, newBalance | TransactionService |
| `ProductionTickEvent` | planetId, deltas map | PlanetaryEconomyManager |
| `ShortageEvent` | planetId, commodityId, deficit | PlanetaryEconomyManager |
| `SurplusEvent` | planetId, commodityId, excess | PlanetaryEconomyManager |

## 6. Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│                  JSON Data Files                     │
│  commodities.json    planet_economies.json           │
└──────────┬──────────────────────┬────────────────────┘
           │                      │
     CommodityRegistry    PlanetaryEconomyManager
           │                      │
           │              (ticks every 10 in-game min)
           │                      │
           │              PlanetData.produce()
           │              PlanetData.consume()
           │                      │
           │              publishes deltas via EventBus
           │                      │
           │              ┌───────▼────────┐
           │              │ PlanetaryStock  │
           │              │ System (ECS)    │
           │              │ applies deltas  │
           │              │ to stations     │
           │              └───────┬─────────┘
           │                      │
           │         ┌────────────▼────────────┐
           └────────►│   Station Entities       │
                     │  MarketComponent (stock)  │
                     │  PricingComponent (prices) │
                     └────────────┬─────────────┘
                                  │
                          PricingSystem (ECS)
                       recalcs prices, NPC restock
                                  │
                     ┌────────────▼─────────────┐
                     │    TransactionService      │
                     │  validates buy/sell         │
                     │  mutates wallet + cargo     │
                     │  mutates station stock      │
                     └────────────┬──────────────┘
                                  │
                          TradeCompletedEvent
                          WalletChangedEvent
                          CargoChangedEvent
                                  │
                          ┌───────▼───────┐
                          │  UI (future)   │
                          │  HUD (future)  │
                          └───────────────┘
```

## 7. File Layout

```
core/src/main/java/com/galacticodyssey/economy/
  data/
    CommodityDefinition.java        — POJO loaded from JSON
    CommodityCategory.java          — Enum: RAW_MATERIAL, REFINED_GOOD, etc.
    CommodityTier.java              — Enum: COMMON through ALIEN
    CommodityRegistry.java          — Loads and indexes commodity definitions
    IndustryType.java               — Enum: MINING, AGRICULTURAL, etc.
    PlanetEconomyData.java          — POJO for planet economy profiles
    PlanetEconomyRegistry.java      — Loads planet economy definitions
    MarketEntry.java                — Stock/demand/supply data per commodity per station
  components/
    MarketComponent.java            — Ashley component: station market state
    PricingComponent.java           — Ashley component: cached prices
    PlayerWalletComponent.java      — Ashley component: player credit balance
    CargoBayComponent.java          — Ashley component: ship bulk cargo
  systems/
    PricingSystem.java              — Ashley IntervalSystem: price recalc + NPC restock
    PlanetaryStockSystem.java       — Ashley system: applies planetary deltas to stations
  simulation/
    PlanetaryEconomyManager.java    — Service: planetary production/consumption tick
    PricingFormula.java             — Static utility: the pricing math, isolated for testing
  service/
    TransactionService.java         — Buy/sell validation and execution
    TradeFailureReason.java         — Enum: INSUFFICIENT_FUNDS, CARGO_FULL, etc.
  events/
    TradeCompletedEvent.java
    TradeFailedEvent.java
    CargoChangedEvent.java
    WalletChangedEvent.java
    ProductionTickEvent.java
    ShortageEvent.java
    SurplusEvent.java

core/src/main/resources/data/economy/
  commodities.json
  planet_economies.json
```

## 8. Testing Strategy

| Test | Type | Validates |
|------|------|-----------|
| CommodityRegistry loads JSON | Unit | Data loading, lookups by ID/category/tier |
| PricingFormula correctness | Unit | Given stock/demand, verify price output matches formula |
| PricingFormula edge cases | Unit | Zero stock, max stock, demand=0, overflow |
| TransactionService buy | Unit | Credits deducted, stock decreased, cargo increased, event published |
| TransactionService sell | Unit | Credits added, stock increased, cargo decreased, event published |
| TransactionService failures | Unit | Insufficient funds, cargo full, no stock, no commodity in cargo |
| PlanetaryEconomy tick | Unit | Production/consumption deltas correct for each industry type |
| PlanetaryEconomy distribution | Unit | Output distributed proportionally to child stations |
| Shortage/surplus detection | Unit | Events published when stock runs low or overflows |
| LOD catch-up | Unit | Distant station catch-up calculation produces reasonable results |
| Full trading loop | Integration | Wire station + planet, run ticks, verify prices shift dynamically |
| Multi-station price divergence | Integration | Two stations with different profiles develop different prices |

## 9. Explicitly Deferred

- **Sector and galactic tiers** — No simulation; architecture supports adding them as event sources later
- **Smuggling / legality** — Commodity `tags` field is present for future legality flags; no scanning or contraband logic
- **Trade routes** — No inter-station NPC hauling; stations restock via `supplyRate` flat value
- **Faction reputation effects on prices** — Hooks exist (tags, events), logic deferred until faction system is built
- **Trading UI** — Event interfaces defined; screens are a separate concern
- **Player-to-player trading** — Server-authoritative multiplayer concern for later
- **Bid/ask spread** — Single price per commodity for now; can add spread later without rework
- **Dynamic contraband** — Legality shifting over time based on faction politics
