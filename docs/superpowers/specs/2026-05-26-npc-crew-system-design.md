# NPC & Crew System Design

## Overview

A unified NPC system where every NPC in the game shares a common identity and stats model. Ship crew members are NPCs that additionally carry crew-specific data (role, rank, morale, loyalty, station assignment). World NPCs can be recruited as crew. AI uses a hybrid Utility AI + Behavior Tree architecture: utility scoring selects the goal, a BT executes it. Morale has hard consequences including desertion and mutiny.

---

## 1. NPC Data Model (Shared Foundation)

Every NPC is an Ashley entity with these core components:

### NpcIdentityComponent

```java
public class NpcIdentityComponent implements Component {
    public String npcId;            // Unique, deterministic from seed for procedural NPCs
    public String name;
    public String species;
    public String background;
    public String portraitId;        // References a portrait asset
    public NpcDisposition disposition; // FRIENDLY, NEUTRAL, HOSTILE
    public String factionId;
    public boolean recruitable;
}
```

### NpcStatsComponent

```java
public class NpcStatsComponent implements Component {
    public float accuracy;  // 0-100, combat hit chance modifier
    public float repair;    // 0-100, repair speed/quality
    public float medical;   // 0-100, healing effectiveness
    public float piloting;  // 0-100, ship handling bonus
    public float science;   // 0-100, scan/analysis speed
    public float combat;    // 0-100, melee/defense effectiveness
}
```

Stats are rolled at NPC generation based on species, background, and role affinity. They grow via XP once recruited as crew.

### NpcDisposition (enum)

`FRIENDLY`, `NEUTRAL`, `HOSTILE`

### NpcScheduleComponent (ambient world NPCs only)

```java
public class NpcScheduleComponent implements Component {
    public List<ScheduleEntry> entries; // (hourOfDay, location, activity)
}
```

Drives day/night behavior for world NPCs. Removed when an NPC is recruited as crew (replaced by station assignments).

---

## 2. Crew Member System

When an NPC is recruited, they gain a `CrewMemberComponent`:

### CrewMemberComponent

```java
public class CrewMemberComponent implements Component {
    public CrewRole role;        // PILOT, GUNNER, ENGINEER, MEDIC, MARINE, SCIENTIST, NAVIGATOR
    public CrewRank rank;        // RECRUIT, CREWMAN, SPECIALIST, VETERAN, OFFICER, COMMANDER
    public float xp;
    public float xpToNextRank;
    public float morale;         // 0-100, starts at 75
    public float loyalty;        // 0-100, starts at 50
    public MoraleState moraleState; // Derived from morale value
    public float wage;           // Periodic credit cost, scales with rank
    public List<String> perkIds; // Unlocked at promotion thresholds
    public Entity assignedStation; // Ship room/station entity, or null
}
```

### CrewRole (enum)

`PILOT`, `GUNNER`, `ENGINEER`, `MEDIC`, `MARINE`, `SCIENTIST`, `NAVIGATOR`

### CrewRank (enum)

`RECRUIT`, `CREWMAN`, `SPECIALIST`, `VETERAN`, `OFFICER`, `COMMANDER`

XP thresholds increase per rank. Promotion is manual (player chooses to promote, costs credits).

**Rank unlock effects:**

| Rank | Unlock |
|------|--------|
| SPECIALIST | Can train adjacent crew (passive XP gain to nearby crew of same role) |
| VETERAN | Autonomous combat decisions (utility AI runs without player orders in combat) |
| OFFICER | Can command a squad, unlocks officer quarters room requirement |
| COMMANDER | Can captain a secondary ship in fleet |

### CrewAssignmentComponent (on room/station entities)

```java
public class CrewAssignmentComponent implements Component {
    public CrewRole requiredRole;
    public Entity assignedCrew;            // NPC entity currently posted here, or null
    public float effectivenessMultiplier;   // Auto-calculated from stats + rank + morale
}
```

### MoraleState (enum)

`CONTENT`, `GRUMBLING`, `DISGRUNTLED`, `MUTINOUS`

### FireReason (enum)

`DISMISSED` (player fired them), `DESERTED` (left due to low morale), `KILLED_IN_ACTION`, `MUTINY_QUELLED`

### Recruitment Flow

1. Player interacts with a recruitable NPC (world NPC with `recruitable = true`)
2. Recruitment dialog shows stats, asks player to pick a role
3. One-time signing bonus deducted from player credits (scaled by NPC stats and rank)
4. `CrewMemberHiredEvent` fired, `CrewMemberComponent` attached to entity
5. NPC entity moves from world to ship interior, `NpcScheduleComponent` removed

---

## 3. AI Architecture (Utility AI + Behavior Trees)

### NpcBrainComponent

```java
public class NpcBrainComponent implements Component {
    public UtilityBrain brain;
    public BehaviorTree<Entity> activeBT;
    public NpcGoal currentGoal;
    public float aiTickInterval;    // LOD-scaled
    public float timeSinceLastTick;
}
```

### NpcGoal (enum)

`MAN_STATION`, `REPAIR_SYSTEM`, `HEAL_CREW`, `FIGHT`, `TAKE_COVER`, `PATROL`, `REST`, `FLEE`, `IDLE`, `FOLLOW_PLAYER`, `TRAIN_CREW`, `EXTINGUISH_FIRE`

### UtilityBrain

A list of `Consideration` objects, each scoring a goal 0.0-1.0 based on world state:

| Consideration | Scores high when | Relevant stats |
|---------------|-----------------|----------------|
| `RepairConsideration` | Ship hull/systems are damaged | `repair` |
| `CombatConsideration` | Hostiles nearby | `combat`, `accuracy` + MARINE/GUNNER role bonus |
| `HealConsideration` | Crew members injured | `medical` (MEDIC role only) |
| `StationConsideration` | Crew is assigned to a station | Baseline, higher for PILOT/NAVIGATOR during flight |
| `RestConsideration` | Fatigue is high | Universal |
| `FireConsideration` | Fire detected in nearby compartment | `repair` + proximity (integrates with existing `FireComponent`) |
| `FleeConsideration` | Health critically low and morale poor | Inverse of `combat` + morale |
| `TrainConsideration` | SPECIALIST+ rank, nearby same-role crew | Role match |

Each consideration returns `(NpcGoal, float score)`. The brain picks the highest-scoring goal. If the goal changed, it swaps the active BT.

### Behavior Tree Library

Each goal maps to a pre-built `BehaviorTree<Entity>`. Example for `REPAIR_SYSTEM`:

```
Sequence
  FindDamagedSystem (selector - picks nearest damaged subsystem)
  NavigateToStation (move to the repair console)
  PerformRepair (play animation, tick repair progress based on repair stat)
  Succeed (goal complete, brain re-evaluates)
```

BTs are loaded from gdx-ai's BT format or constructed programmatically. Custom leaf/task nodes live in `npc/ai/behaviors/`.

### LOD AI Tiers

| Tier | Distance | Utility Eval | BT Tick | Pathfinding |
|------|----------|-------------|---------|-------------|
| Full | < ~50m from player | Every 0.5s | Every frame | Full nav graph |
| Medium | Same ship, far from player | Every 2s | Every 0.5s | Simplified |
| Background | Different ship / off-screen | Every 5-10s | None | None — stat-based resolution |

**Background resolution:** No BT runs. Instead, a stat-based probability roll determines outcome. Example: "Engineer with 80 repair stat has 90% chance to fix this system this tick."

---

## 4. Morale & Loyalty System

### Morale Inputs

Applied each tick of `MoraleSystem` (every ~2s game time):

| Input | Effect |
|-------|--------|
| Wage satisfaction | Underpaid: -0.5/tick. Overpaid: +0.1/tick |
| Living conditions | No quarters / overcrowded: -0.3/tick. Private quarters: +0.2/tick |
| Leadership skill | Player's Leadership skill provides flat morale floor lift; at max, morale decays 50% slower |
| Combat stress | Post-combat: -5 to -15. Witnessed death: additional -10 |
| Idle time | No station, nothing to do: -0.1/tick |
| Loyalty buffer | Loyalty >75 dampens morale loss by 25% |
| Rest | In REST goal: +0.2/tick |

### Morale States & Consequences

| Range | State | Effects |
|-------|-------|---------|
| 80-100 | `CONTENT` | +10% effectiveness bonus on all tasks |
| 50-79 | `GRUMBLING` | Normal performance. Occasional complaint dialog lines |
| 25-49 | `DISGRUNTLED` | -15% effectiveness. May refuse dangerous orders (FIGHT/REPAIR under fire). `CrewInsubordinationEvent` fired |
| 0-24 | `MUTINOUS` | -30% effectiveness. Chance to desert at ports (`CrewDesertedEvent`). Multiple mutinous crew may trigger `MutinyEvent` |

### Loyalty

- Grows slowly while morale is `CONTENT`: +0.05/tick
- Grows on promotion (+10), successful mission (+2-5), player saving their life (+15)
- Drops on wage cuts (-10), witnessed crew death (-5), prolonged `DISGRUNTLED` state (-0.1/tick)
- High loyalty crew (>80) warn the player about mutiny plans from other crew

### MutinyEvent Resolution

When triggered, the player gets a timed choice:

1. **Negotiate** — Leadership skill check. Success: morale reset to 50 for mutineers. Failure: escalates to force.
2. **Quell by force** — Combat encounter with mutinous crew inside the ship.
3. **Concede demands** — Wage increase + morale reset. All crew loyalty drops slightly (they learn demands work).

Outcome affects loyalty of all crew, not just mutineers.

---

## 5. Integration Points

### Ship Rooms & Station Assignments

`RoomType` maps to `CrewRole`:

| RoomType | CrewRole |
|----------|----------|
| COCKPIT | PILOT |
| ENGINE_ROOM | ENGINEER |
| MEDBAY | MEDIC |
| ARMORY | MARINE |
| CREW_QUARTERS | (any, for rest) |
| CARGO_BAY | (any, for logistics) |

Each room entity with a `CrewAssignmentComponent` becomes a station. `CrewAssignmentSystem` calculates `effectivenessMultiplier` using:

```
baseStat = relevant stat for the role (0-100), normalized to 0.0-1.0
rankBonus = rank.ordinal() * 0.05 (0.0 for RECRUIT up to 0.25 for COMMANDER)
moraleMod = moraleState == CONTENT ? 1.1 : moraleState == DISGRUNTLED ? 0.85 : moraleState == MUTINOUS ? 0.7 : 1.0
effectivenessMultiplier = (baseStat + rankBonus) * moraleMod
```

This multiplier feeds into existing systems (repair speed, turret accuracy, etc.).

### Life Support Integration

`CrewMetabolicSystem` currently reads `atmo.crewCount` as a raw int. Updated to query NPC entities with `CrewMemberComponent` whose position is inside that compartment's bounds. Activity level maps from `NpcGoal`:

| NpcGoal | Activity Level |
|---------|---------------|
| REST, IDLE | RESTING (1.0x) |
| MAN_STATION, PATROL, TRAIN_CREW | LIGHT_WORK (1.5x) |
| REPAIR_SYSTEM, EXTINGUISH_FIRE | HEAVY_WORK (3.0x) |
| FIGHT, TAKE_COVER, FLEE | COMBAT (4.0x) |

### Combat Integration

- **GUNNER at turret:** `accuracy` stat modifies turret base accuracy from `TurretTrackingSystem`
- **MARINE during boarding:** Spawned as combatant entities reusing existing `CombatAIComponent` + `SquadComponent`, stats fed from `NpcStatsComponent`
- **Crew injury:** Crew take damage via existing `HealthComponent`. MEDIC crew heal injured crew via `HEAL_CREW` goal
- **Crew death in combat:** Triggers `CrewMemberFiredEvent` with `FireReason.KILLED_IN_ACTION`, morale hit to witnesses

### Economy Integration

- `CrewWageSystem` ticks on configurable interval (every in-game day). Deducts wages from player credits
- If player can't pay: morale takes -10 per missed payday
- Recruitment costs one-time signing bonus scaled by NPC stats and rank

---

## 6. Events

| Event | Payload |
|-------|---------|
| `CrewMemberHiredEvent` | `Entity npc, CrewRole role` |
| `CrewMemberFiredEvent` | `Entity npc, FireReason reason` |
| `CrewPromotedEvent` | `Entity npc, CrewRank oldRank, CrewRank newRank` |
| `MoraleChangedEvent` | `Entity npc, MoraleState oldState, MoraleState newState` |
| `CrewInsubordinationEvent` | `Entity npc, NpcGoal refusedGoal` |
| `CrewDesertedEvent` | `Entity npc, String locationId` |
| `MutinyEvent` | `List<Entity> mutineers` |
| `CrewAssignedEvent` | `Entity npc, Entity station` |
| `CrewInjuredEvent` | `Entity npc, float damage` |

---

## 7. ECS Systems & Processing Order

| System | Priority | Tick Rate | Description |
|--------|----------|-----------|-------------|
| `NpcAISystem` | 20 | LOD-scaled (0.5s-10s) | Utility brain evaluation, BT swap/tick |
| `CrewAssignmentSystem` | 21 | 1s | Recalculates effectiveness multipliers |
| `MoraleSystem` | 22 | 2s (game time) | Updates morale/loyalty, state transitions |
| `CrewWageSystem` | 23 | Per in-game day | Deducts wages, handles missed pay |
| `CrewXPSystem` | 24 | On event | Awards XP, checks promotion eligibility |
| `NpcNavigationSystem` | 25 | Per frame | Pathfinding/movement for BT navigate nodes |
| `NpcScheduleSystem` | 26 | 5s | Drives ambient world NPC schedules (non-crew) |

### NPC Navigation

Crew NPCs pathfind within ship interiors using a nav graph derived from `InteriorLayout` room connectivity. Each room is a node, corridors are edges. Within a room, NPCs move to a specific station point via simple steering behaviors (gdx-ai `SteeringBehavior`).

---

## 8. Data Files

| File | Purpose |
|------|---------|
| `data/npcs/species.json` | Species definitions with stat affinities and portrait pools |
| `data/npcs/backgrounds.json` | Backgrounds with stat modifiers and flavor text |
| `data/npcs/perks.json` | Perk definitions unlocked at rank thresholds |
| `data/npcs/names.json` | Name pools per species for procedural generation |

---

## 9. Package Layout

```
npc/
  components/         NpcIdentityComponent, NpcStatsComponent, NpcScheduleComponent
  crew/               CrewMemberComponent, CrewAssignmentComponent, CrewRole, CrewRank, MoraleState
  ai/                 NpcBrainComponent, UtilityBrain, Consideration, NpcGoal
  ai/considerations/  RepairConsideration, CombatConsideration, HealConsideration, etc.
  ai/behaviors/       BT task/leaf nodes (NavigateToTask, PerformRepairTask, etc.)
  systems/            NpcAISystem, MoraleSystem, CrewAssignmentSystem, CrewWageSystem,
                      CrewXPSystem, NpcNavigationSystem, NpcScheduleSystem
  events/             All crew/NPC events
  data/               NpcDataRegistry, NpcGenerator (procedural NPC creation from seed)
```

---

## 10. Implementation Phases

1. **Phase 1 — Data model & crew management:** NPC components, crew components, enums, events, `NpcDataRegistry`, `NpcGenerator`, `CrewAssignmentSystem`, `CrewXPSystem`. Unit tests for all.
2. **Phase 2 — Morale & economy:** `MoraleSystem`, `CrewWageSystem`, morale state transitions, loyalty. Integration with player Leadership skill and economy.
3. **Phase 3 — AI core:** `UtilityBrain`, considerations, `NpcBrainComponent`, `NpcAISystem` with LOD tiers. BT library for core goals (MAN_STATION, REPAIR_SYSTEM, REST, IDLE).
4. **Phase 4 — Navigation & spatial:** `NpcNavigationSystem`, ship interior nav graph from `InteriorLayout`, steering behaviors. Crew NPCs physically move between rooms.
5. **Phase 5 — Combat & advanced BTs:** Combat-related considerations and BTs (FIGHT, TAKE_COVER, FLEE, HEAL_CREW). Integration with existing combat systems. Boarding scenario support.
6. **Phase 6 — World NPCs & recruitment:** `NpcScheduleComponent`, `NpcScheduleSystem`, recruitment flow, world NPC generation. Ambient NPC behavior in cities/stations.
7. **Phase 7 — Life support & advanced integration:** Wire crew into `CrewMetabolicSystem`, fire response (EXTINGUISH_FIRE), mutiny event resolution, training (TRAIN_CREW).
