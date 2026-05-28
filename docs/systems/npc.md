# NPC & Crew Systems

The `npc` package covers procedural NPC generation, the crew assignment and morale system, experience and rank progression, and the data model that gives each NPC a distinct identity.

---

## NPC Generation

**`NpcGenerator`**

Procedurally generates a complete NPC by combining data from `NpcDataRegistry`:

1. Pick a `SpeciesDefinition` (stat bonuses, trait pool).
2. Pick a `BackgroundDefinition` (starting skills, narrative hooks).
3. Roll personality traits from the species trait pool, filtering contradictory pairs (e.g. "cowardly" and "brave" cannot coexist).
4. Derive `NpcDisposition` and `NpcGoal` from personality weights.
5. Generate a name using species-appropriate phoneme rules.
6. Assign a portrait index.
7. Compute `NpcStatsComponent` values: might, dexterity, intellect, willpower, charisma, cunning — seeded from background and species modifiers.

The generator is deterministic given a seed, so NPCs can be regenerated identically across save/load cycles without storing all fields.

**`NpcDataRegistry`**

Loads species, background, and perk definitions from `data/npcs/`. Provides lookup maps used by `NpcGenerator` and the crew UI.

| Data class | Content |
|---|---|
| `SpeciesDefinition` | Stat bonuses, trait pool, available backgrounds |
| `BackgroundDefinition` | Starting skill levels, narrative hook categories |
| `PerkDefinition` | Special ability, trigger condition, effect formula |

---

## Crew Assignment

**`CrewAssignmentSystem`** (priority 21)

Tracks which crew members are assigned to which ship roles. Each frame, computes an **effectiveness multiplier** for each assigned crew member:

```
effectiveness = baseSkill(role) × rankBonus × moraleModifier
```

The multiplier is written to `CrewAssignmentComponent.effectivenessMultiplier` and read by the ship systems that need it (e.g. engine crew boosts thrust efficiency, gunners improve weapon heat dissipation, medics reduce injury recovery time).

When a crew member is unassigned or morale drops to `BROKEN`, their effectiveness is zeroed.

---

## Experience & Rank

**`CrewXPSystem`**

Awards XP to crew members for relevant actions:
- Pilot crew gain XP on atmospheric entry and hyperspace jumps.
- Gunners gain XP on weapon kills.
- Engineers gain XP on repair actions.
- Medics gain XP on healing events.

When accumulated XP crosses the next rank threshold defined in `CrewRank`, the crew member is promoted. Each `CrewRank` step increases the rank bonus used in the effectiveness formula.

---

## Morale

`MoraleState` is an enum: `ELATED` → `CONTENT` → `NEUTRAL` → `UNHAPPY` → `BROKEN`.

Morale changes are published as `CrewMoralChangeEvent`. Systems that affect morale (life support degradation, combat losses, successful missions) call into the crew component directly or via events. The `CrewAssignmentSystem` reads morale when computing effectiveness each frame.

---

## NPC Schedules

`NpcScheduleComponent` stores a list of `ScheduleEntry` objects, each specifying:
- Time of day (game-time hours)
- Target location (room or station ID)
- Activity (sleeping, working, eating, patrolling)

NPC AI reads the schedule to determine where the NPC should be at any given game time. This is used for ambient life simulation on stations and in ship interiors.

---

## Components Reference

| Component | Key fields |
|---|---|
| `NpcIdentityComponent` | Name, species ID, background ID, portrait index, disposition, age |
| `NpcPersonalityComponent` | Trait list (`PersonalityTrait[]`), goals (`NpcGoal[]`), faction dispositions |
| `NpcBackstoryComponent` | Hook list (`HookType[]`), narrative details |
| `NpcScheduleComponent` | List of `ScheduleEntry` (time, location, activity) |
| `NpcStatsComponent` | Might, dexterity, intellect, willpower, charisma, cunning (0–100) |
| `CrewMemberComponent` | `CrewRank`, `CrewRole`, health, morale, accumulated XP |
| `CrewAssignmentComponent` | Assigned station ID, `effectivenessMultiplier` |

---

## Enums

| Enum | Values |
|---|---|
| `PersonalityTrait` | Brave, cowardly, loyal, greedy, compassionate, ruthless, curious, etc. |
| `NpcDisposition` | Friendly, neutral, suspicious, hostile |
| `NpcGoal` | Survival, wealth, power, knowledge, freedom, revenge, etc. |
| `HookType` | FamilyTie, DebtOwed, FugitiveRecord, LostRelative, SecretSkill, etc. |
| `CrewRole` | Pilot, Engineer, Gunner, Medic, Navigator, Security, Quartermaster |
| `CrewRank` | Recruit → Crewman → PettyOfficer → Lieutenant → Commander → Captain |
| `MoraleState` | Elated, Content, Neutral, Unhappy, Broken |
| `NPCRole` | Trader, Guard, Civilian, Pirate, Mission-giver, etc. |

---

## Events

| Event | When published |
|---|---|
| `NpcStateChangedEvent` | NPC changes activity, location, or disposition |
| `CrewMoralChangeEvent` | Crew member morale level changes (includes old and new state) |
