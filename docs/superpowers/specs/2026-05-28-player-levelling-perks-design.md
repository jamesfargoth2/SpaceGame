# Player Levelling & Perks — Completion Design

**Date:** 2026-05-28
**Status:** Approved (brainstorming) — pending spec review
**Branch context:** `coresystemFinish`

## 1. Goal & scope

Complete the Player Levelling & Perks system. The data model, `RealTimeSkillSystem`
(character-level math, `awardSkillXP`, `spendPoint`), and the three level/skill/perk
events already exist and match `docs/DESIGN.md` §4.6 and the `libgdx-player-rpg-stats`
skill. This pass closes the four remaining gaps identified in `docs/TODO-systems.md`:

1. **XP is never awarded** — nothing calls `awardSkillXP()`; the progression loop is dead.
2. **No player perk system** — `PlayerStatsComponent.perks` is never populated; the only
   `PerkDefinition`/`perks.json` that exist are for NPC crew. `PerkAvailableEvent` is
   published but unconsumed.
3. **Perks have no effects** — `PlayerStatQuery` never reads `perks`.
4. **No UI** — no level-up notification, no skill-point allocation, no perk selection.

Persistence of player stats (currently absent) is **in scope**.

### Approved decisions

- **Scope:** full end-to-end (XP hooks + perk system + effects + UI + persistence).
- **Perk structure:** per-skill perk trees, one tree per real-time skill (9 trees).
- **Perk effects:** hybrid — data-driven modifiers + a small set of named-id special perks.
- **XP wiring:** centralized event-listener system (not scattered calls).
- **UI:** full `CharacterScreen` tab (skills overview + point allocation + perk-tree panel)
  plus a transient level-up toast overlay.
- **Content:** author a tree for **all 9 real-time skills** now (~3 tiers each).
- **Persistence:** add `PlayerStatsSnapshot` to the existing snapshot system.

### Non-goals

- No respec / point refund (allocation is permanent — enforced in API and UI).
- No perk trees anchored to point-skills in this pass (real-time skill trees only).
- No new combat/economy mechanics; only existing stat surfaces gain perk modifiers.
- No multiplayer/server replication of stats beyond what already exists.

## 2. Existing foundation (do not rebuild)

| Element | Location |
|---|---|
| `PlayerStatsComponent` | `player/components/` — realTimeSkills, pointSkills, characterLevel, totalXP, unspentPoints, `Array<String> perks` |
| `RealTimeSkill` (9), `PointSkill` (8), `SkillProgress` | `player/stats/` |
| `RealTimeSkillSystem` | `player/systems/` — `awardSkillXP`, `spendPoint`, `checkCharacterLevelUp`, publishes the 3 events |
| `PlayerStatQuery`, `SkillCheck` | `player/stats/` |
| `SkillLevelUpEvent`, `CharacterLevelUpEvent`, `PerkAvailableEvent` | `player/events/` |
| Wiring | `GameWorld` registers the system; player entity gets `PlayerStatsComponent` |

Patterns to follow:
- **Events:** `EventBus.subscribe(Class, listener)` / `publish(event)`.
- **Persistence:** components implement `Snapshotable<S>` (`takeSnapshot`/`restoreFromSnapshot`),
  registered in `SnapshotComponentRegistry` by type-name.
- **UI:** screens implement `ManagedScreen`, registered as tabs in `ScreenTabManager`
  (existing tabs: Inventory, Quest Journal, Outfitter, Crew).

## 3. Perk data model & registry

### Data file
`core/src/main/resources/data/player/perk_trees.json` — distinct from NPC `perks.json`.

### Types (in `player/stats/`)

```
PerkNodeDef {
  String id;                       // unique, e.g. "firearms_steady_aim"
  String name;
  String description;
  String treeSkill;                // RealTimeSkill enum name, e.g. "FIREARMS"
  int    tier;                     // depth, 0 = root
  int    requiredSkillLevel;       // gate on the anchoring skill's level
  Array<String> prerequisitePerkIds; // parent nodes that must be owned
  Array<PerkModifier> modifiers;   // data-driven effects (may be empty)
  String specialEffectId;          // optional named-handler id (nullable)
}

PerkModifier { PerkTarget target; ModifierOp op; float value; }   // op ∈ {ADD, MULT}

PerkTree { RealTimeSkill skill; Array<PerkNodeDef> nodes; }       // built from JSON
```

`PerkTarget` enum enumerates the stat surfaces perks can affect — at minimum the existing
`PlayerStatQuery` outputs (TRADE_PRICE, REP_GAIN, MAX_CREW, CRAFT_QUALITY, HAZARD_RESIST,
HEAL_EFF, SCAN_QUALITY) plus combat surfaces (BALLISTIC_DAMAGE, ENERGY_DAMAGE,
MELEE_DAMAGE, RELOAD_SPEED, SPRINT_DURATION). The enum is the bounded modifier vocabulary;
anything outside it is a `specialEffectId`.

### `PerkRegistry` (in `player/stats/`)
Loads `perk_trees.json` once at startup (constructed in `GameWorld` like other registries).

- `Array<PerkNodeDef> getTree(RealTimeSkill skill)`
- `PerkNodeDef get(String perkId)`
- `boolean canSelect(PlayerStatsComponent stats, String perkId)` — true iff not already
  owned, `requiredSkillLevel` met, and all `prerequisitePerkIds` owned.
- `float aggregateModifiers(PlayerStatsComponent stats, PerkTarget target)` — folds owned
  perks' matching modifiers: starts at additive 0 / multiplicative 1, returns a combined
  result the queries apply.
- `boolean has(PlayerStatsComponent stats, String specialEffectId)` — for named handlers.

## 4. Perk selection logic — `PerkSystem`

New `EntitySystem` in `player/systems/` (logic only; no GL dependency).

- Add `int unspentPerkPicks = 0` to `PlayerStatsComponent`. The existing
  `checkCharacterLevelUp` in `RealTimeSkillSystem` already fires `PerkAvailableEvent` every
  5 levels; it will also `stats.unspentPerkPicks++` at that point.
- `boolean selectPerk(Entity player, String perkId)`: returns false unless
  `unspentPerkPicks > 0` and `PerkRegistry.canSelect(...)`. On success: add id to
  `stats.perks`, decrement `unspentPerkPicks`, publish new `PerkSelectedEvent(player, perkId)`.
- Permanent: no removal API.

## 5. Perk effects via `PlayerStatQuery`

Each existing `PlayerStatQuery` method additionally folds in
`perkRegistry.aggregateModifiers(stats, <relevant target>)`. To keep `PlayerStatQuery`
static and free of state, the `PerkRegistry` reference is passed in (overloaded methods
that accept a `PerkRegistry`, with the existing no-perk signatures kept for callers that
don't yet have one, or a lightweight injected singleton accessor). Final approach chosen in
the plan; the constraint is that **callers in economy/crafting/combat get perk effects
without each knowing about perks**.

Special (named-id) perks: documented set checked at their site via `PerkRegistry.has(...)`.
The starter content keeps these to a minimum (most perks are pure modifiers); any special
perk added is listed in the system doc with its handler location.

## 6. XP-award integration — `SkillXpAwardSystem`

New `EntitySystem` in `player/systems/`. On construction it `subscribe`s to gameplay events;
it only awards XP when the acting entity is the player. Calls
`realTimeSkillSystem.awardSkillXP(player, skill, baseXP, difficulty)`.

| Skill | Source | Notes |
|---|---|---|
| Firearms | `DamageDealtEvent`/`EntityKilledEvent`, ballistic weapon | split from Energy by weapon category |
| Energy Weapons | same, energy weapon category | |
| Melee | `MeleeHitEvent` | |
| Mining | `ResourceCollectedEvent` | |
| Trading | market buy/sell transaction | difficulty scales with transaction value |
| Stealth | periodic tick while undetected near hostiles (`AwarenessState`) | small XP per interval |
| Piloting | ship flight/maneuver integration | accumulated, awarded on threshold |
| Athletics | sprint distance from `MovementStateComponent` | accumulated, awarded on threshold |
| Repair | repair-complete event | |

**Verification during planning:** confirm each source event carries enough info to identify
the player as source and (for weapons) the weapon category. Where it does not, add the
minimal field rather than guessing. Any real-time skill with no usable source yet gets a
documented TODO hook (no silent omission).

XP is scaled by a `difficultyMultiplier` per the skill's guidance (never flat).

## 7. UI

### 7a. `CharacterScreen` (`ManagedScreen`, "Character" tab)
Registered in the same place as the other tabs (`GameScreen`). Three regions:

1. **Header / overview:** character level, total XP, progress bar to next character level,
   `unspentPoints` and `unspentPerkPicks` badges.
2. **Skills:** 9 real-time skills (level + per-skill XP progress bar, read-only) and 8 point
   skills (level + `+` button that calls `RealTimeSkillSystem.spendPoint`; disabled at cap or
   when no points; a confirm step communicates permanence).
3. **Perk trees:** tab/selector across the 9 real-time skill trees; nodes drawn by tier with
   prerequisite links; a node is selectable (highlighted, click → `PerkSystem.selectPerk`)
   only when `canSelect` and `unspentPerkPicks > 0`; owned nodes marked; locked nodes show
   their requirement. Reads only via `PerkRegistry` + queries; never mutates the component
   directly.

### 7b. Level-up toast overlay
Transient HUD overlay subscribing to `CharacterLevelUpEvent`, `SkillLevelUpEvent`,
`PerkAvailableEvent`. Shows brief auto-dismissing notifications ("Level Up! +2 skill points",
"Firearms → 12", "Perk available — open Character"). Pure presentation; no logic.

UI must not contain simulation logic (architectural rule #5); it reads the component and
calls system APIs.

## 8. Persistence

- `PlayerStatsComponent implements Snapshotable<PlayerStatsSnapshot>`.
- New `PlayerStatsSnapshot` in `persistence/snapshots/`: `characterLevel`, `totalXP`,
  `unspentPoints`, `unspentPerkPicks`, real-time skill map (skill name → {level, xp}),
  point skill map (skill name → int), `perks` list.
- Register `"PlayerStats"` in `SnapshotComponentRegistry`.
- Verify the entity-snapshot builder includes the component for the player entity (add to
  its persisted-component set if it is allow-listed there).

## 9. Testing (headless, no GL context)

- `PerkRegistry`: prereq gating (`canSelect`) across skill-level + parent-perk conditions;
  modifier aggregation (ADD and MULT) for a target.
- `PerkSystem`: `selectPerk` honours `unspentPerkPicks`, prereqs, and no-duplicate; publishes
  `PerkSelectedEvent`; permanence (no removal).
- `PlayerStatQuery`: perk modifiers correctly fold into each affected query.
- `SkillXpAwardSystem`: each wired event maps to the correct skill and awards scaled XP only
  for player-sourced actions.
- `RealTimeSkillSystem`: `unspentPerkPicks` increments at the 5-level boundary (extend
  existing coverage if present).
- `PlayerStatsSnapshot`: round-trip (take → restore) preserves all fields including maps and
  perks.

UI screens follow the project's existing UI-test convention (logic-bearing actors may get a
light test; rendering is verified by running the game).

## 10. File summary

**New**
- `data/player/perk_trees.json` (9 trees)
- `player/stats/PerkNodeDef.java`, `PerkModifier.java`, `PerkTarget.java`, `ModifierOp.java`,
  `PerkTree.java`, `PerkRegistry.java`
- `player/systems/PerkSystem.java`, `player/systems/SkillXpAwardSystem.java`
- `player/events/PerkSelectedEvent.java`
- `ui/CharacterScreen.java` (+ any small sub-actors), level-up toast overlay
- `persistence/snapshots/PlayerStatsSnapshot.java`
- Tests for each new logic unit

**Modified**
- `PlayerStatsComponent` (+`unspentPerkPicks`, implements `Snapshotable`)
- `RealTimeSkillSystem` (increment `unspentPerkPicks` on perk-available)
- `PlayerStatQuery` (fold perk modifiers)
- `SnapshotComponentRegistry` (register PlayerStats)
- `GameWorld` (construct `PerkRegistry`, add `PerkSystem` + `SkillXpAwardSystem`)
- `GameScreen` (register Character tab, add toast overlay)
- whichever events need a source/weapon-category field for XP attribution
- `docs/systems/player.md`, `docs/TODO-systems.md` (mark implemented)
