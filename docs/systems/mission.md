# Mission & Quest Systems

The `mission` package implements three layers of player-driven content: the low-level **objective tracking** that listens to world events, the **job system** for repeatable procedural contracts, and the **saga system** for structured narrative quest chains.

---

## Objective Tracking

**`ObjectiveTrackingSystem`**

The objective tracker subscribes to gameplay events from the event bus and advances quest progress automatically — no other system needs to know a quest is active.

Subscribed events and the objective types they advance:

| Event | Objective type |
|---|---|
| `LocationEnteredEvent` | `REACH_LOCATION` |
| `ScanCompleteEvent` | `SCAN_OBJECT` |
| `EntityKilledEvent` | `DESTROY_TARGET` |
| `CargoBayUpdateEvent` | `COLLECT_RESOURCE` |
| `CargoDeliveredEvent` | `DELIVER_CARGO` |

When an objective's completion condition is met, the system updates the `Objective` in `QuestJournal` and publishes `ObjectiveCompletedEvent`. When all objectives in a quest are complete, `QuestCompletedEvent` is published, which triggers reward processing and saga graph advancement.

**`QuestJournal`**

Owned by the player entity (component). Stores the list of active quests and their objectives. The HUD quest tracker reads from `QuestJournal` directly each frame.

**`Objective`**

A single quest step with:
- `ObjectiveType` (reach, destroy, collect, deliver, scan)
- Target descriptor (entity ID, location ID, commodity type, etc.)
- Current count and required count (for collect/destroy objectives)
- Completion flag

---

## Job System

The job system generates short procedural contracts that refresh at stations. They are content-independent — all parameters are populated at generation time.

**`JobTemplate`** — Defines the contract type: `JobType` (combat, salvage, exploration, delivery), required faction reputation, reward range, and `ObjectiveTemplate` list.

**`JobInstance`** — A live contract: the template with all variables resolved (target entity, delivery location, commodity quantities, etc.). Tracks `JobState`:

| State | Meaning |
|---|---|
| `AVAILABLE` | Shown on the station job board |
| `ACTIVE` | Player accepted; objectives tracking |
| `COMPLETED` | All objectives met; awaiting reward collection |
| `FAILED` | Time limit expired or failure condition triggered |

**`SectorContext`** — Provides the location data (system ID, station ID, patrol zone) used when instantiating a job template.

**`ReputationQuery`** — Checks if the player meets the faction reputation requirement before allowing job acceptance.

---

## Saga System

Sagas are structured narrative quest chains modelled as a **directed graph**: nodes are quest steps, edges are transitions that activate based on player choices or world state.

**`SagaRegistry`** — Loads saga definitions from `data/missions/sagas/`. Provides lookup by saga ID.

**`SagaInstance`** — Tracks player progress through one active saga:
- Current `SagaState` per node (locked / available / active / completed)
- Which edges have been traversed
- Accumulated consequence flags

**`SagaData`** — The static definition: a list of `SagaNodeData` and `SagaEdgeData`, plus metadata (category, recommended level).

**`SagaNodeData`** — A single node:
- `SagaNodeType` (quest, choice, consequence, epilogue)
- List of associated `ObjectiveTemplate`
- Dialogue script reference

**`SagaEdgeData`** — A transition between nodes:
- Source and destination node IDs
- `TriggerData` (condition that must be true for the edge to activate: player choice, world flag, reputation threshold)

**`TriggerData`** — Encodes a single unlock condition: `ObjectiveCompleted`, `FactionReputation`, `ItemInInventory`, `LocationVisited`, or a boolean world flag.

---

## Discovery System

**`DiscoveryLead`** — Represents an exploration discovery (anomaly, derelict, ruin) that the player has found but not yet fully investigated. Stored in `QuestJournal` as a separate list from active quests; can evolve into a full saga when investigated.

---

## Data Layout

```
data/missions/
  jobs/           ← JobTemplate JSON files
  sagas/          ← SagaData JSON files (one per saga)
  objectives/     ← Shared ObjectiveTemplate definitions
```

---

## Events Reference

| Event | When published |
|---|---|
| `ObjectiveUpdatedEvent` | Objective progress count changes (before completion) |
| `ObjectiveCompletedEvent` | Individual objective fully completed |
| `QuestCompletedEvent` | All objectives in a quest/job are done |
