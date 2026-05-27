---
name: libgdx-npc-crew-system
description: >
  Enforces correct NPC crew management, role assignment, morale simulation,
  XP progression, promotion perks, and station posting for a libGDX 3D space
  game using Ashley ECS. Use this skill whenever writing or modifying: crew
  recruitment, crew role definitions, morale calculations, crew XP and leveling,
  promotion thresholds, crew station assignment on ships, crew AI behavior
  during combat, or NPC schedule/behavior systems. Also triggers when adding
  crew loyalty mechanics, autonomous crew decision-making, or crew-affects-gameplay
  modifiers like repair speed or turret accuracy.
---

# libGDX NPC / Crew System

## Crew Data Model

Every crew member is an Ashley entity with a `CrewComponent` carrying their persistent state:

```java
public class CrewComponent implements Component, Pool.Poolable {
    public String  name;
    public String  species;
    public String  background;
    public CrewRole role        = CrewRole.MARINE;
    public CrewRank rank        = CrewRank.RECRUIT;

    public float accuracy       = 50f;   // 0-100
    public float repairSkill    = 50f;
    public float medicalSkill   = 50f;
    public float morale         = 75f;   // 0-100
    public float loyalty        = 50f;   // 0-100

    public float xp             = 0f;
    public int   level          = 1;
    public Entity assignedStation;       // ship room entity, null = unassigned

    @Override public void reset() {
        name = null; species = null; background = null;
        role = CrewRole.MARINE; rank = CrewRank.RECRUIT;
        accuracy = repairSkill = medicalSkill = 50f;
        morale = 75f; loyalty = 50f; xp = 0f; level = 1;
        assignedStation = null;
    }
}
```

## Roles and Ranks

```java
public enum CrewRole {
    PILOT, GUNNER, ENGINEER, MEDIC, MARINE, SCIENTIST, NAVIGATOR
}

public enum CrewRank {
    RECRUIT(0),       // base tier
    CREWMAN(100),     // basic competence
    SPECIALIST(500),  // can train others in their role
    VETERAN(1500),    // autonomous combat decisions
    OFFICER(4000),    // can command a wing/squad; unlocks officer quarters
    COMMANDER(10000); // can captain a secondary ship

    public final float xpThreshold;
    CrewRank(float xpThreshold) { this.xpThreshold = xpThreshold; }
}
```

Ranks gate gameplay features — Specialist crew can passively train lower-ranked crew in the same role, Veterans reduce player micromanagement by acting autonomously during combat, Officers unlock fleet command, and Commanders can captain secondary ships. Tie these unlocks to rank checks, not arbitrary level numbers.

## XP and Promotion

```java
public class CrewProgressionSystem extends IteratingSystem {
    public CrewProgressionSystem() {
        super(Family.all(CrewComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        CrewComponent crew = Mappers.crew.get(entity);
        CrewRank nextRank = getNextRank(crew.rank);
        if (nextRank != null && crew.xp >= nextRank.xpThreshold) {
            crew.rank = nextRank;
            EventBus.post(new CrewPromotionEvent(entity, crew.rank));
        }
    }

    private CrewRank getNextRank(CrewRank current) {
        CrewRank[] ranks = CrewRank.values();
        int idx = current.ordinal();
        return (idx + 1 < ranks.length) ? ranks[idx + 1] : null;
    }
}
```

XP sources — award XP through events, not direct mutation. Systems that generate crew XP (combat, missions, repairs) post a `CrewXPEvent(entity, amount, source)`. A dedicated listener applies the gain, modified by the player's Leadership skill:

```java
public void onCrewXP(CrewXPEvent event) {
    CrewComponent crew = Mappers.crew.get(event.entity);
    float leadershipBonus = 1f + playerStats.leadership * 0.005f;
    crew.xp += event.amount * leadershipBonus;
}
```

## Morale Simulation

Morale is the crew's emotional state and directly affects performance. Update it each game tick as a weighted sum of factors:

| Factor | Effect on Morale | Source |
|---|---|---|
| Pay rate | +0.1/tick if paid above base, -0.3/tick if underpaid | Economy system |
| Living conditions | +0.05 to -0.2 depending on ship quarters quality | Ship room component |
| Leadership skill | +0.01 * leadershipLevel per tick | Player RPG stats |
| Combat losses | -5 per allied crew death | Combat events |
| Mission success | +3 per completed mission | Mission events |
| Idle time (no action) | -0.02/tick if > 5 min real time | Internal timer |

```java
public class MoraleSystem extends IteratingSystem {
    public MoraleSystem() {
        super(Family.all(CrewComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        CrewComponent crew = Mappers.crew.get(entity);
        float delta = 0f;
        delta += computePayEffect(crew);
        delta += computeQuartersEffect(crew);
        delta += computeLeadershipEffect();
        crew.morale = MathUtils.clamp(crew.morale + delta * dt, 0f, 100f);

        if (crew.morale < 20f) {
            EventBus.post(new CrewMoraleWarning(entity, crew.morale));
        }
    }
}
```

When morale drops below 20, crew become unreliable — reduce their stat contributions by 50%. Below 10 they may desert (post a `CrewDesertionEvent` that the ship system handles).

## Station Assignment

Crew post to specific ship rooms (turrets, engineering bay, medbay, bridge). The station entity carries a `StationComponent` specifying which role it needs:

```java
public class StationComponent implements Component {
    public CrewRole requiredRole;
    public Entity   assignedCrew;   // null = unmanned
    public float    efficiencyBonus; // how much this station benefits from skilled crew
}
```

When a crew member is assigned, their role-specific stats modify the station's output. A gunner at a turret adds their accuracy to the turret's base accuracy. An engineer in the engine room speeds repairs by their repairSkill percentage. Unassigned stations operate at 60% base efficiency.

```java
public float getStationEfficiency(Entity station) {
    StationComponent sc = Mappers.station.get(station);
    if (sc.assignedCrew == null) return 0.6f;
    CrewComponent crew = Mappers.crew.get(sc.assignedCrew);
    float skillValue = getRelevantSkill(crew, sc.requiredRole);
    return 0.6f + 0.4f * (skillValue / 100f);
}
```

## Combat Behavior

During combat, crew behavior depends on rank:

- **Recruit-Crewman:** Execute only explicit player orders (manual turret aiming, repair targets)
- **Specialist:** Prioritize targets within their assigned station's arc autonomously
- **Veteran:** Make tactical decisions — reroute power, prioritize critical repairs, switch ammo types
- **Officer+:** Coordinate multiple stations, issue orders to lower-ranked crew nearby

Implement this with a simple priority check in the combat AI:

```java
public boolean canActAutonomously(CrewComponent crew) {
    return crew.rank.ordinal() >= CrewRank.VETERAN.ordinal()
        && crew.morale > 30f;
}
```

## Specialist Training

Specialists passively train lower-ranked crew in the same role when both are aboard the same ship. Training happens as a slow XP drip:

```java
public void processTraining(Array<Entity> shipCrew, float dt) {
    for (Entity trainer : shipCrew) {
        CrewComponent tc = Mappers.crew.get(trainer);
        if (tc.rank.ordinal() < CrewRank.SPECIALIST.ordinal()) continue;
        for (Entity trainee : shipCrew) {
            CrewComponent sc = Mappers.crew.get(trainee);
            if (sc == tc || sc.role != tc.role) continue;
            if (sc.rank.ordinal() >= tc.rank.ordinal()) continue;
            sc.xp += 0.5f * dt; // passive training XP
        }
    }
}
```

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Base morale | 75 | Starting morale for new recruits |
| Morale desert threshold | 10 | Below this, crew may desert |
| Morale warning threshold | 20 | UI warning triggers |
| Leadership XP multiplier | 0.5% per level | How much Leadership boosts crew XP gain |
| Unassigned station efficiency | 60% | Base output when no crew posted |
| Training XP rate | 0.5/sec | Passive XP from specialist training |
| Autonomous morale floor | 30 | Below this, even Veterans won't act autonomously |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Mutating crew XP directly from combat code | Post `CrewXPEvent` — let the listener apply Leadership bonuses |
| Forgetting morale clamp | Always clamp morale to [0, 100] |
| Station efficiency ignoring empty posts | Unassigned stations must default to 60%, not 0% or 100% |
| Rank checks using level instead of rank enum | Use `rank.ordinal()` comparisons, not raw level numbers |
| Crew desertion without event | Always post `CrewDesertionEvent` so ship/UI systems can react |
| Training everyone regardless of role | Training only applies between same-role crew members |
