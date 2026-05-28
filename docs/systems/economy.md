# Economy Systems

The `economy` package implements a layered trade simulation: commodity production at planetary economies, dynamic pricing through supply and demand, and player buy/sell transactions at market stations.

---

## Architecture Overview

The economy is tiered: galactic-scale flows influence sector averages, which set baseline prices for each planet. Individual market stations on that planet inherit the planet price but fluctuate independently based on local stock levels.

```
Galaxy average → Sector average → PlanetaryEconomyManager → PlanetaryStockSystem → MarketComponent
```

---

## Production & Stock

**`PlanetaryEconomyManager`**

Drives the planet-level economic simulation. On each production cycle (a configurable game-time interval), calculates output from the planet's `IndustryType` and `EconomyType`, then publishes `ProductionTickEvent` with the output quantities.

**`PlanetaryStockSystem`** (priority 51)

Subscribes to `ProductionTickEvent`. Updates `MarketComponent.stock` for each commodity produced. Stock is indexed by station ID for fast lookup. Stock is bounded at `MarketEntry.maxStock`; overproduction is discarded. Stock drains as players and NPC traders purchase.

---

## Pricing

**`PricingSystem`**

Recomputes commodity prices each tick using a supply/demand curve:
- Base price comes from `CommodityDefinition.basePrice` loaded from data.
- `SupplyLevel` (abundance → scarcity) shifts price along the curve.
- Per-station modifiers in `PricingComponent` apply faction markup, import/export bonuses.
- Final price is written to `MarketEntry.price` in `MarketComponent`.

Price changes may publish `PriceFluctuationEvent` when a commodity crosses a significant threshold (used by NPC traders and the player's market watch alerts).

---

## Transactions

**`TransactionService`**

Validates and executes buy/sell transactions:
1. Confirms the commodity is in stock (`MarketEntry.stock > 0`).
2. Confirms the buyer has sufficient credits in `PlayerWalletComponent`.
3. Deducts credits, reduces stock, and adds the commodity to `CargoBayComponent`.
4. Calculates profit for sale transactions and awards XP to trade skills.
5. Returns a result with success flag or `TradeFailureReason` (insufficient funds, no stock, cargo full, etc.).

---

## Data

**`CommodityRegistry`**

Loads `data/economy/commodities.json` at startup. Provides lookup by commodity ID.

| Data class | Purpose |
|---|---|
| `CommodityDefinition` | Name, category, base price, weight, volume |
| `CommodityCategory` | Type enum: food, fuel, minerals, electronics, organics, luxury, contraband |
| `CommodityTier` | Rarity tier affecting base price (common / uncommon / rare / exotic) |
| `IndustryType` | Sector industry (agriculture, mining, manufacturing, research, military) |

**`PlanetEconomyRegistry`**

Loads per-planet economy configurations (`data/economy/planets/`):

| Data class | Purpose |
|---|---|
| `PlanetEconomyData` | Economy archetype for a planet, list of produced/demanded commodities |
| `EconomyType` | Archetype enum (agricultural, industrial, frontier, resort, military, etc.) |
| `SupplyLevel` | Abundance enum: plentiful / adequate / limited / scarce |
| `MarketEntry` | Single commodity in a station market: current price, stock, max stock |

---

## Components Reference

| Component | Key fields |
|---|---|
| `MarketComponent` | Map of commodity ID → `MarketEntry`; station owner faction |
| `CargoBayComponent` | List of commodity stacks, current weight, current volume, max weight, max volume |
| `PlayerWalletComponent` | `credits` (long) |
| `PricingComponent` | Per-station markup multipliers, import/export bonus map |

---

## Events Reference

| Event | When published |
|---|---|
| `ProductionTickEvent` | Planet produces commodities (per production cycle) |
| `PriceFluctuationEvent` | Commodity price crosses a significant threshold |
