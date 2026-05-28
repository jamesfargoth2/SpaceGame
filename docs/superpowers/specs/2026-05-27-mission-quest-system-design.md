# Mission / Quest System Design

**Date:** 2026-05-27
**Branch:** procgen
**Status:** Approved

---

## 1. Overview

The mission system is a two-tier hybrid: **Sagas** for handcrafted narrative content (main story, faction chains, companion arcs) and **Jobs** for procedurally generated content (job board + event-driven discovery missions). Both tiers share a common Objective and Reward infrastructure. A **QuestDiscoverySystem** bridges them, listening to the EventBus and activating quests through a two-path discovery model (rumour from an NPC, or physical location reached).

**Package:** `core/src/main/java/com/galacticodyssey/mission/`

---

## 2. Architecture

```
                ┌─────────────────────────────────────────┐
                │           QuestDiscoverySystem          │
                │  (EventBus listener — bridges tiers)    │
                └──────────┬──────────────┬───────────────┘
                           │              │
          ┌────────────────▼──┐      ┌────▼──────────────────┐
          │   SAGA TIER       │      │   JOB TIER            │
          │                   │      │                       │
          │  SagaRunner        │      │  JobBoard (per sector)│
          │  SagaGraph         │      │  ProceduralJobGen     │
          │  SagaInstance      │      │  EventJobGen          │
          └────────────────┬──┘      └────┬──────────────────┘
                           │              │
          ┌────────────────▼──────────────▼──────────────────┐
          │               SHARED INFRASTRUCTURE               │
          │   Objective   ObjectiveTrackingSystem             │
          │   MissionReward    RewardSystem                   │
          │   QuestJournal     (player-facing state)          │
          └───────────────────────────────────────────────────┘
```

---

## 3. Saga System (Handcrafted Quests)

### 3.1 Saga Graph Model

A Saga is a directed graph of `SagaNode` objects. Each node is one discrete stage. Conditional edges connect nodes; the path taken depends on player choices and world state.

**Node types:**

| Type | Purpose |
|---|---|
| `OBJECTIVE` | Player completes one or more objectives to advance |
| `DIALOGUE_CHOICE` | Player makes a narrative decision; edge taken depends on the choice key recorded |
| `CONSEQUENCE` | Fires world events (reputation change, NPC death, faction war) — auto-advances immediately |
| `TERMINUS` | End node; marks the saga completed or failed |

### 3.2 Data Model

```
SagaData (from YAML, immutable)
  ├── id, title
  ├── category: MAIN_STORY | FACTION | COMPANION
  ├── Array<SagaNodeData>    — node definitions
  ├── Array<SagaEdgeData>    — transitions with optional conditions
  └── Array<TriggerData>     — what activates this saga

SagaInstance (runtime, mutable)
  ├── sagaDataId
  ├── currentNodeId
  ├── Map<String,String> choicesMade   — records every dialogue decision
  ├── SagaState: LOCKED | AVAILABLE | ACTIVE | COMPLETE | FAILED
  └── Array<Objective> activeObjectives
```

`choicesMade` is the branching record. Later nodes use it as an edge condition (`requiresChoice: sided_with_guild`). Branch state is always explicit — never inferred from world state.

### 3.3 SagaRunner (Ashley EntitySystem)

- `update(dt)` — checks time-limited nodes for expiry
- Subscribes to `ObjectiveCompletedEvent` → checks if all objectives on current node are done → evaluates outbound edges → advances to next node
- Subscribes to `DialogueChoiceMadeEvent` → takes the matching edge out of a `DIALOGUE_CHOICE` node
- On node entry: publishes `SagaNodeEnteredEvent` (UI, audio, VFX subscribe independently)
- On `CONSEQUENCE` node entry: publishes configured world events, then immediately auto-advances

### 3.4 YAML Format

```yaml
id: main_act1_the_signal
category: MAIN_STORY
triggers:
  - type: REPUTATION_THRESHOLD
    faction: explorers_guild
    minStanding: 10

nodes:
  - id: find_the_anomaly
    type: OBJECTIVE
    objectives:
      - type: SCAN_OBJECT
        targetId: anomaly_k7_signal
  - id: report_choice
    type: DIALOGUE_CHOICE
    npcId: commander_varek
  - id: trust_the_guild
    type: CONSEQUENCE
    events:
      - type: REPUTATION_CHANGE
        faction: explorers_guild
        delta: 15
  - id: end_act1
    type: TERMINUS
    outcome: COMPLETE

edges:
  - from: find_the_anomaly
    to: report_choice
  - from: report_choice
    to: trust_the_guild
    condition: { choice: sided_with_guild }
  - from: trust_the_guild
    to: end_act1
```

---

## 4. Job System (Procedural Quests)

### 4.1 Data Model

```
JobTemplate (from JSON, immutable)
  ├── id, type (JobType enum)
  ├── giverFactionTag        — which factions offer this job type
  ├── requiredStanding       — minimum faction rep to see it on the board
  ├── baseReward
  ├── Array<ObjectiveTemplate>
  └── discoveryMode: BOARD | EVENT_DRIVEN | BOTH

JobInstance (runtime, mutable)
  ├── templateId, instanceId (UUID)
  ├── JobState: RUMOURED | AVAILABLE | ACTIVE | COMPLETE | FAILED | EXPIRED
  ├── giverNpcId, giverLocationId
  ├── difficulty (1–10), playerLevelAtGen
  ├── float timeLimit, elapsed
  ├── Array<Objective> objectives
  ├── MissionReward reward
  └── DiscoveryLead lead     — null for BOARD jobs, populated for EVENT_DRIVEN
```

**JobType enum:** `CARGO_HAUL`, `BOUNTY_HUNT`, `MERCENARY`, `ESCORT`, `MINING_CONTRACT`, `EXPLORATION_SURVEY`, `SALVAGE`

### 4.2 JobBoard (per sector/station)

- Holds a cap of 8 available jobs per station or settlement
- Refreshes every 300 seconds via `ProceduralJobGenerator`
- Jobs are filtered by player faction standing at **display time** (not generation time) — standing changes are immediately reflected
- Board jobs begin in `AVAILABLE`; player accepts → `ACTIVE`

### 4.3 ProceduralJobGenerator

Instantiates a `JobTemplate` against the current `SectorData` to produce a concrete `JobInstance`. Variation comes from sector context — the same `CARGO_HAUL` template produces a legal milk run in a safe sector and a smuggling run through contested space.

```
generate(template, sector, playerLevel, reputation):
  1. Resolve targetIds and locationIds from sector data
  2. Scale difficulty: baseDifficulty * (1.0 + playerLevel * 0.15)
  3. Scale reward: baseReward * (1.0 + playerLevel * 0.1) * standingBonus
  4. Return JobInstance in AVAILABLE state
```

### 4.4 EventJobGenerator (Reactive Jobs)

Subscribes to world events and spawns jobs in `RUMOURED` state with a populated `DiscoveryLead`:

| World Event | Job type spawned |
|---|---|
| `FactionWarStartedEvent` | `MERCENARY` — destroy N faction ships |
| `ShipMissingEvent` | `SALVAGE` — recover cargo/data at last known coords |
| `AnomalyDetectedEvent` | `EXPLORATION_SURVEY` — scan the anomaly location |
| `CargoShipAttackedEvent` | `BOUNTY_HUNT` + `CARGO_HAUL` (recover stolen goods) |

Reactive jobs carry the id of the triggering world event. When a `FactionWarEndedEvent` (or equivalent resolution event) fires, all `RUMOURED` and `AVAILABLE` jobs referencing that war id are expired. `ACTIVE` jobs run to completion — the player is already committed.

### 4.5 Time Limits

Applied only where narrative delay matters (escort, mercenary). Generated as `1.5 × estimated_completion_time`. Board jobs without a time limit never expire while `ACTIVE` — only while `AVAILABLE` on the board (they rotate off at the next refresh).

### 4.6 Job Template JSON Format

```json
{
  "id": "cargo_haul_legal",
  "type": "CARGO_HAUL",
  "giverFactionTag": "trade_guilds",
  "requiredStanding": -25,
  "discoveryMode": "BOARD",
  "baseReward": {
    "credits": 800,
    "reputationDelta": 5,
    "reputationFaction": "trade_guilds"
  },
  "objectives": [
    { "type": "DELIVER_CARGO", "requiredCount": 1 }
  ]
}
```

---

## 5. Discovery Pipeline

### 5.1 DiscoveryLead

Every event-driven job carries a `DiscoveryLead`:

```
DiscoveryLead
  ├── jobInstanceId
  ├── rumourNpcIds[]         — 1–3 NPCs in the sector who "know" about the job
  ├── locationId             — physical place to reach/scan for cold activation
  ├── boolean rumourHeard
  ├── boolean locationDiscovered
  └── long expiresAt         — world-time; if triggering event resolves before this, job expires
```

A `RUMOURED` job activates (`→ ACTIVE`) when **either** `rumourHeard` or `locationDiscovered` is true. Both paths are valid:

- **Rumour path:** Player talks to any NPC in `rumourNpcIds` → job activates → map marker appears for the location
- **Location path:** Player stumbles on the location without hearing the rumour → job activates cold with no prior context (intentional — rewards exploration)

### 5.2 QuestDiscoverySystem (Ashley EntitySystem)

| Event subscribed | Action |
|---|---|
| `WorldStateChangeEvent` | Calls `EventJobGenerator` to spawn `JobInstance` in `RUMOURED` state |
| `NpcDialogueEvent` (topic=RUMOUR) | Checks NPC id against all active `DiscoveryLead.rumourNpcIds` → marks `rumourHeard=true`, activates job, posts `QuestDiscoveredEvent` |
| `LocationEnteredEvent` | Checks locationId against all active `DiscoveryLead.locationId` → marks `locationDiscovered=true`, activates job, posts `QuestDiscoveredEvent` |
| `ScanCompleteEvent` | Same as `LocationEnteredEvent` for deep-space scannable objects |
| `WorldStateChangeEvent` (resolution) | Expires all `RUMOURED` leads tied to the resolved event id |

**Saga trigger checking:** On every relevant event (reputation threshold events, location events, quest completion events), `QuestDiscoverySystem` also checks all `LOCKED` sagas against their `TriggerData`. When conditions are met it sets the saga to `AVAILABLE` and posts `SagaActivatedEvent`.

**NPC rumour assignment:** When `EventJobGenerator` creates a job, it asks the sector's NPC registry for 1–3 NPCs plausibly connected to the event (faction-aligned, geographically near). No NPC ids are hardcoded in quest data — the lead is assembled at runtime.

---

## 6. Shared Infrastructure

### 6.1 Objective

```
Objective
  ├── id, type (ObjectiveType enum)
  ├── targetId
  ├── int requiredCount, currentCount
  ├── boolean optional
  └── boolean completed
```

**ObjectiveType enum:** `DELIVER_CARGO`, `DESTROY_TARGET`, `REACH_LOCATION`, `SCAN_OBJECT`, `COLLECT_RESOURCE`, `SURVIVE_TIME`, `ESCORT_TARGET`, `TALK_TO_NPC`

### 6.2 ObjectiveTrackingSystem (Ashley EntitySystem)

Subscribes to all relevant EventBus events and increments matching objective counts. Never polls — always event-driven. `SURVIVE_TIME` objectives are the only type updated in `update(dt)`.

Publishes:
- `ObjectiveUpdatedEvent` — consumed by HUD tracker
- `ObjectiveCompletedEvent` — consumed by `SagaRunner` and `JobBoard`

### 6.3 MissionReward

```
MissionReward
  ├── int credits
  ├── ObjectMap<String, Integer> resources
  ├── String reputationFaction
  ├── float reputationDelta
  ├── float crewXP
  └── Array<String> itemRewards
```

Optional objectives grant a 25% bonus on credits and resources. `RewardSystem` distributes all rewards and publishes `ReputationChangeEvent` — never mutates reputation directly.

### 6.4 Quest Journal

```
QuestJournal
  ├── SagaInstance      activeMainStory         — 0 or 1
  ├── Array<SagaInstance> activeFactionChains   — one per faction, soft cap ~4
  ├── Array<SagaInstance> activeCompanionArcs   — one per companion
  ├── Array<JobInstance>  activeJobs            — hard cap: 10
  └── Array<JobInstance>  rumourBoard           — discovered but not yet accepted leads
```

`rumourBoard` is the player-visible staging area for `RUMOURED` jobs. Accepting a rumour moves it to `activeJobs`.

**Persistence:** `QuestJournal` serialises to JSON via the save system. `SagaInstance.choicesMade` is the branching record — on load, `SagaRunner` reconstructs graph position from the saved `currentNodeId` and validates outbound edges against the stored choices.

---

## 7. EventBus Integration

All cross-system boundaries use published events. No direct references from the mission system into UI, audio, or other game systems.

| Event published | Consumers |
|---|---|
| `QuestDiscoveredEvent` | UI (journal notification), audio (discovery sting) |
| `SagaActivatedEvent` | UI, NPC dialogue system |
| `SagaNodeEnteredEvent` | Dialogue system, cutscene trigger, UI |
| `ObjectiveUpdatedEvent` | HUD objective tracker |
| `ObjectiveCompletedEvent` | SagaRunner, JobBoard |
| `QuestCompletedEvent` | RewardSystem, UI, audio |
| `QuestFailedEvent` | UI, reputation system |
| `ReputationChangeEvent` | Faction reputation system |

---

## 8. Reputation Gating

`JobBoard` reads the player's live reputation component at display time — no caching. `SagaData` triggers check live reputation thresholds the same way. Standing changes are immediately reflected in available quests.

Reputation thresholds from the design doc:

| Standing | Label | Unlock |
|---|---|---|
| +25 | Friendly | Faction job board access |
| +50 | Allied | Faction story chain activation |
| +75 | Honored | Unique ships, crew recruits, endgame faction missions |

---

## 9. File Layout

```
core/src/main/resources/data/quests/
  story/
    act1_the_signal.yaml
    act2_the_reckoning.yaml
  factions/
    syndicate_chain.yaml
    explorers_guild_chain.yaml
  companions/
    crew_varek_arc.yaml
  jobs/
    templates.json          — all procedural job templates
    event_triggers.json     — world event → job type mappings

core/src/main/java/com/galacticodyssey/mission/
  saga/
    SagaData.java
    SagaNodeData.java
    SagaEdgeData.java
    SagaInstance.java
    SagaRunner.java
  job/
    JobTemplate.java
    JobInstance.java
    JobBoard.java
    ProceduralJobGenerator.java
    EventJobGenerator.java
  discovery/
    DiscoveryLead.java
    QuestDiscoverySystem.java
  shared/
    Objective.java
    ObjectiveType.java
    ObjectiveTrackingSystem.java
    MissionReward.java
    RewardSystem.java
    QuestJournal.java
  events/
    QuestDiscoveredEvent.java
    SagaActivatedEvent.java
    SagaNodeEnteredEvent.java
    ObjectiveUpdatedEvent.java
    ObjectiveCompletedEvent.java
    QuestCompletedEvent.java
    QuestFailedEvent.java
```

---

## 10. Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Board refresh interval | 300s | Job board regeneration frequency per station |
| Board hand size | 8 | Max jobs visible on a single board |
| Max active jobs | 10 | Hard cap on `QuestJournal.activeJobs` |
| Difficulty scale factor | `1.0 + playerLevel * 0.15` | Enemy count / health scaling |
| Reward scale factor | `1.0 + playerLevel * 0.1` | Credits / resource scaling |
| Optional objective bonus | 1.25× | Multiplier on credits + resources for optional completion |
| Time limit buffer | 1.5× estimated completion | Prevents impossible time windows |
| Max rumour NPCs per lead | 3 | How many NPCs can surface a given rumour |
