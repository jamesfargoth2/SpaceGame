# Faction/Reputation System Design

## Overview

A data-driven reputation system that tracks the player's standing with each faction on a -100 to +100 scale. Reputation changes ripple to related factions based on diplomatic relations. Consequences include docking rights, trade pricing, mission availability, and encounter hostility. Built as a hybrid: `PlayerReputationComponent` stores data on the player entity, `ReputationManager` handles event-driven mutation logic and implements the existing `ReputationQuery` interface.

## Architecture: Hybrid Component + Manager

`PlayerReputationComponent` (Ashley Component on the player entity) stores the standings map. `ReputationManager` (event-driven service, not an IteratingSystem) subscribes to `ReputationChangeEvent`, applies Diplomacy skill modifiers, computes ripple effects via `PoliticalRelationGraph`, clamps values, detects tier crossings, and publishes `ReputationTierChangedEvent`. It implements `ReputationQuery` so existing mission/job systems work without changes.

---

## Data Model

### PlayerReputationComponent

Attached to the player entity. Stores per-faction standings.

| Field | Type | Description |
|---|---|---|
| `standings` | `Map<String, Float>` | Faction ID to standing value, clamped [-100, +100] |

Default standing for any faction not in the map: 0.0 (Neutral).

### ReputationTier Enum

Maps numeric ranges to named tiers. Used for consequence lookups and UI display.

| Tier | Min (inclusive) | Max (exclusive) | Description |
|---|---|---|---|
| `HOSTILE` | -100 | -50 | KOS, no docking, military pursuit |
| `UNFRIENDLY` | -50 | 0 | Denied most services, higher prices |
| `NEUTRAL` | 0 | 25 | Basic trading and docking |
| `FRIENDLY` | 25 | 50 | Discounts, faction missions |
| `ALLIED` | 50 | 75 | Military hardware, faction bases |
| `HONORED` | 75 | 100 | Unique ships, crew recruits, story missions |
| `EXALTED` | 100 | 100 | Faction leadership influence, endgame |

`ReputationTier.fromStanding(float)` returns the tier for a given standing value. EXALTED requires exactly 100.

### ReputationTierChangedEvent

Published when a player crosses a tier boundary.

| Field | Type | Description |
|---|---|---|
| `factionId` | `String` | Which faction |
| `oldTier` | `ReputationTier` | Previous tier |
| `newTier` | `ReputationTier` | New tier |
| `newStanding` | `float` | Current standing value |

### ReputationConfigData

Loaded from `data/factions/reputation_config.json`. Tunable constants.

| Field | Type | Default | Description |
|---|---|---|---|
| `combatKillPenalty` | `float` | -15.0 | Rep loss for killing a faction NPC/ship |
| `tradeBonus` | `float` | 1.0 | Rep gain per trade transaction |
| `smugglingPenaltyCaught` | `float` | -20.0 | Rep loss when caught smuggling |
| `diplomacyGainMultPerLevel` | `float` | 0.05 | +5% gain per Diplomacy level |
| `diplomacyLossReductionPerLevel` | `float` | 0.03 | -3% loss per Diplomacy level |
| `diplomacyMaxLossReduction` | `float` | 0.5 | Losses never reduced below 50% |

### Ripple Fractions (by PoliticalRelation)

When faction A receives a reputation delta, related factions receive a fraction. Positive ripple means same-sign (helping A's ally helps you with them too). Negative ripple means inverse-sign (helping A's enemy hurts you with A).

| Relation to Primary | Ripple Fraction | Direction |
|---|---|---|
| ALLIED | 0.50 | Same sign |
| FRIENDLY | 0.25 | Same sign |
| NEUTRAL | 0.00 | No ripple |
| TENSE | 0.10 | Inverse sign |
| HOSTILE | 0.25 | Inverse sign |
| WAR | 0.50 | Inverse sign |

Ripple deltas do NOT cascade further. Only the primary event triggers ripple.

---

## ReputationManager

Event-driven service. Not an IteratingSystem (reputation changes are sporadic, not per-tick).

### Dependencies

- `EventBus` — subscribes to `ReputationChangeEvent`, publishes `ReputationTierChangedEvent`
- `PlayerReputationComponent` — reads/writes standings (obtained from player entity)
- `PoliticalRelationGraph` data — `Map<String, Map<String, PoliticalRelation>>` for ripple lookup
- `PlayerStatsComponent` — reads Diplomacy skill level for modifiers
- `ReputationConfigData` — tunable constants

### Implements

`ReputationQuery` (`com.galacticodyssey.mission.job.ReputationQuery`) — the existing functional interface used by `JobBoard` and `ProceduralJobGenerator`.

### Processing Flow

When `ReputationChangeEvent` is received:

1. **Read Diplomacy level** from `PlayerStatsComponent.pointSkills["Diplomacy"]` (default 0 if absent)
2. **Apply Diplomacy modifier:**
   - If delta > 0: `delta *= (1.0 + diplomacyLevel * diplomacyGainMultPerLevel)`
   - If delta < 0: `reduction = min(diplomacyLevel * diplomacyLossReductionPerLevel, diplomacyMaxLossReduction)` then `delta *= (1.0 - reduction)`
3. **Apply primary delta** to `standings[event.factionId]`, clamp to [-100, +100]
4. **Check tier crossing** on primary faction, publish `ReputationTierChangedEvent` if changed
5. **Compute ripple:** For each other faction with a relation to `event.factionId`:
   - Look up `PoliticalRelation` between primary faction and this faction
   - Compute ripple delta: `primaryDelta * rippleFraction * sign` (where sign is +1 for allied/friendly, -1 for tense/hostile/war)
   - Apply ripple delta to `standings[otherFactionId]`, clamp
   - Check tier crossing, publish event if changed
6. Ripple deltas do NOT trigger further ripple

### Public API

```
float getStanding(String factionId)        // ReputationQuery implementation
ReputationTier getTier(String factionId)   // Convenience
void setPlayerEntity(Entity player)        // Called during game init
```

---

## Reputation Sources

### 1. Mission Completion (existing)

Already wired. `RewardSystem` publishes `ReputationChangeEvent` when a completed mission has `MissionReward.reputationFaction` and `reputationDelta` set. No changes needed.

### 2. Combat — Killing Faction NPCs

A new listener on `EntityKilledEvent`. When the killed entity has `NpcIdentityComponent.factionId`:
- Publish `ReputationChangeEvent(factionId, combatKillPenalty, "combat:" + killedEntityId)`

Placed in `ReputationManager` as an additional event subscription.

### 3. Trade

When `TransactionService` completes a trade at a faction-controlled station:
- Publish `ReputationChangeEvent(stationFactionId, tradeBonus, "trade:" + transactionId)`

Small modification to `TransactionService` — check if the station/market has a faction owner and publish the event.

### 4. Smuggling (if caught)

When the player is caught trading contraband at a faction station:
- Publish `ReputationChangeEvent(factionId, smugglingPenaltyCaught, "smuggling:" + transactionId)`

Detection logic is a future system. This spec only defines the event source point — the `ReputationChangeEvent` contract is ready for it.

---

## Reputation Consequences

### 1. Docking Rights

When the player attempts to dock at a faction-controlled station, check `ReputationManager.getTier(factionId)`. If HOSTILE: deny docking, publish `DockingDeniedEvent(stationId, factionId, reason)`. Stations in neutral/unclaimed space always allow docking.

### 2. Price Modifiers

`TransactionService` applies a reputation-based price multiplier when trading at a faction station:

| Tier | Buy Multiplier | Sell Multiplier |
|---|---|---|
| HOSTILE | denied | denied |
| UNFRIENDLY | 1.20 | 0.80 |
| NEUTRAL | 1.00 | 1.00 |
| FRIENDLY | 0.95 | 1.05 |
| ALLIED | 0.90 | 1.10 |
| HONORED | 0.85 | 1.15 |
| EXALTED | 0.80 | 1.20 |

Defined in `ReputationTier` as constants for each tier.

### 3. Mission Availability

Already wired. `JobBoard.getAvailableJobs(ReputationQuery)` filters by `JobTemplate.requiredStanding`. `ProceduralJobGenerator` scales rewards via standing bonus. Both take `ReputationQuery` — passing `ReputationManager` makes them work.

### 4. Encounter Hostility

`EncounterContext` already accepts `playerReputation`. Wire `ReputationManager.getStanding(owningFactionId)` into encounter generation. When HOSTILE or worse, faction patrols spawn as hostile encounters.

---

## New Events

| Event | Fields | Published by | Consumed by |
|---|---|---|---|
| `ReputationTierChangedEvent` | `factionId, oldTier, newTier, newStanding` | `ReputationManager` | UI notifications, NPC behavior |
| `DockingDeniedEvent` | `stationId, factionId, reason` | Docking system | UI, audio |

### Existing Events Reused

| Event | Role in reputation flow |
|---|---|
| `ReputationChangeEvent` | Published by RewardSystem, combat listener, trade. Consumed by ReputationManager |
| `EntityKilledEvent` | ReputationManager listens for faction NPC kills |

---

## Data Files

### reputation_config.json

Location: `core/src/main/resources/data/factions/reputation_config.json`

```json
{
  "combatKillPenalty": -15.0,
  "tradeBonus": 1.0,
  "smugglingPenaltyCaught": -20.0,
  "diplomacyGainMultPerLevel": 0.05,
  "diplomacyLossReductionPerLevel": 0.03,
  "diplomacyMaxLossReduction": 0.5
}
```

---

## Persistence

`PlayerReputationComponent.standings` is serialized into `SaveBundle.factionState` as a `Map<String, Float>`. On load, the map is read back and used to reconstruct the component.

---

## Summary of Changes

| Category | Item | Type |
|---|---|---|
| New component | `PlayerReputationComponent` | New file |
| New service | `ReputationManager` | New file |
| New data class | `ReputationConfigData` | New file |
| New enum | `ReputationTier` | New file |
| New event | `ReputationTierChangedEvent` | New file |
| New event | `DockingDeniedEvent` | New file |
| New data file | `factions/reputation_config.json` | New resource |
| Modified service | `TransactionService` — publish trade rep event | Small edit |
| Modified system | Docking system — check reputation before docking | Small edit |
| Modified wiring | `EncounterContext` creation — pass real reputation | Small edit |
| Modified wiring | `JobBoard`/`ProceduralJobGenerator` — pass `ReputationManager` as `ReputationQuery` | Small edit |
| Modified persistence | `SaveWriter`/`SaveReader` — serialize standings | Small edit |

---

## Future Expansion

- **Smuggling detection system** — publishes `ReputationChangeEvent` when contraband is found
- **Faction-exclusive crew recruitment** — unlocked at HONORED/EXALTED tiers
- **Unique faction ships** — purchasable at EXALTED tier
- **Faction leadership influence** — endgame content at EXALTED
- **Reputation decay** — optional, configurable per-faction
- **Faction war dynamic events** — reputation swings during faction conflicts
