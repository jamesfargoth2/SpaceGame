---
name: libgdx-mission-quest-system
description: >
  Enforces correct mission/quest architecture including procedural job board
  generation, handcrafted quest lines, objective tracking, reward distribution,
  and quest state persistence for a libGDX 3D space game using Ashley ECS.
  Use this skill whenever writing or modifying: procedural mission templates
  (cargo hauling, bounty hunting, escort, mining, salvage, survey), handcrafted
  quest scripting, quest objective definitions, mission reward calculations,
  quest journal UI, mission board filtering by faction, or any quest-related
  state machine. Also triggers when adding companion quest lines, discovery-triggered
  quests, mission difficulty scaling, or branching quest outcomes.
---

# libGDX Mission / Quest System

## Architecture

The mission system has two tiers — procedural jobs generated at runtime and handcrafted quests defined in data files. Both share the same objective tracking and reward infrastructure.

```
MissionRegistry (data-driven)
  |- ProceduralJobGenerator   -> creates MissionInstance from templates
  |- HandcraftedQuestLoader   -> loads quest chains from JSON/YAML
  +- MissionTracker           -> tracks active missions per player
       |- ObjectiveSystem     -> checks objective completion each tick
       +- RewardSystem        -> distributes rewards on completion
```

## Mission Data Model

```java
public class MissionInstance {
    public String       id;
    public String       templateId;
    public MissionType  type;
    public MissionState state = MissionState.AVAILABLE;
    public String       giverFaction;
    public float        difficulty;    // 1-10 scale
    public String       title;
    public String       description;

    public Array<Objective> objectives = new Array<>();
    public MissionReward    reward     = new MissionReward();
    public float            timeLimit; // seconds, 0 = no limit
    public float            elapsed;
}

public enum MissionType {
    CARGO_HAUL, BOUNTY_HUNT, MERCENARY, EXPLORATION_SURVEY,
    MINING_CONTRACT, ESCORT, SALVAGE, STORY, COMPANION, DISCOVERY
}

public enum MissionState {
    AVAILABLE, ACCEPTED, IN_PROGRESS, COMPLETED, FAILED, EXPIRED
}
```

## Objective System

```java
public class Objective {
    public String        id;
    public ObjectiveType type;
    public String        targetId;
    public int           requiredCount;
    public int           currentCount;
    public boolean       completed;
    public boolean       optional;
}

public enum ObjectiveType {
    DELIVER_CARGO, DESTROY_TARGET, REACH_LOCATION, SCAN_OBJECT,
    COLLECT_RESOURCE, SURVIVE_TIME, ESCORT_TARGET, TALK_TO_NPC
}
```

The `ObjectiveTrackingSystem` listens for game events and updates objectives:

```java
public class ObjectiveTrackingSystem extends EntitySystem {
    @Override
    public void update(float dt) {
        for (MissionInstance mission : activeMissions) {
            if (mission.timeLimit > 0) {
                mission.elapsed += dt;
                if (mission.elapsed >= mission.timeLimit) failMission(mission);
            }
        }
    }

    public void onCargoDelivered(CargoDeliveredEvent e) {
        for (MissionInstance m : activeMissions) {
            for (Objective obj : m.objectives) {
                if (obj.type == ObjectiveType.DELIVER_CARGO
                    && obj.targetId.equals(e.cargoType)) {
                    obj.currentCount++;
                    if (obj.currentCount >= obj.requiredCount) obj.completed = true;
                    checkMissionComplete(m);
                }
            }
        }
    }
}
```

## Procedural Job Generation

```java
public class ProceduralJobGenerator {
    public Array<MissionInstance> generateBoard(SectorData sector,
                                                ReputationComponent rep,
                                                int playerLevel) {
        Array<MissionInstance> board = new Array<>();
        for (MissionTemplate t : missionRegistry.getTemplates()) {
            if (!meetsRequirements(t, rep, playerLevel)) continue;
            MissionInstance job = instantiate(t, sector);
            scaleDifficulty(job, playerLevel);
            scaleRewards(job, playerLevel, rep.getStanding(job.giverFaction));
            board.add(job);
        }
        return board;
    }
}
```

## Procedural Mission Templates

| Template | Objectives | Key Variation |
|---|---|---|
| Cargo Haul | DELIVER_CARGO to destination | Legal vs smuggling; cargo type and quantity |
| Bounty Hunt | DESTROY_TARGET (specific NPC) | Target difficulty; dead-or-alive option |
| Mercenary | DESTROY_TARGET (N faction ships) | Faction conflict context |
| Exploration Survey | SCAN_OBJECT at N locations | Distance; hazardous regions |
| Mining Contract | COLLECT_RESOURCE (type, amount) | Resource rarity; location danger |
| Escort | ESCORT_TARGET to destination | Convoy size; ambush probability |
| Salvage | REACH_LOCATION + COLLECT_RESOURCE | Wreck in dangerous area; time pressure |

## Handcrafted Quest Chains

Story quests defined in JSON with explicit sequencing and discovery triggers:

```java
public void onDiscovery(DiscoveryEvent event) {
    Array<QuestData> triggered = questRegistry.getByTrigger("discovery", event.targetId);
    for (QuestData q : triggered) {
        if (!isAlreadyCompleted(q.id) && meetsPrereqs(q)) {
            activateMission(q);
            EventBus.post(new QuestDiscoveredEvent(q));
        }
    }
}
```

## Rewards

```java
public class MissionReward {
    public int    credits;
    public ObjectMap<String, Integer> resources = new ObjectMap<>();
    public float  reputationDelta;
    public String reputationFaction;
    public float  crewXP;
    public Array<String> itemRewards = new Array<>();
}
```

Optional objectives grant 25% bonus credits and resources on top of base.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Board refresh interval | 300s (5 min) | Job board regeneration frequency |
| Max active missions | 10 | Prevents quest log overload |
| Difficulty scale factor | 1.0 + playerLevel * 0.15 | Enemy count/health scaling |
| Reward scale factor | 1.0 + playerLevel * 0.1 | Credits/resources scaling |
| Optional bonus multiplier | 1.25x | Bonus for completing optional objectives |
| Time limit buffer | 1.5x estimated completion | Prevents impossible time limits |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Hardcoding quest content in Java | Define all quests in JSON/YAML data files |
| Objective tracking via polling every frame | Use event listeners for each objective type |
| Forgetting prerequisite checks on quest chains | Always verify prerequisites before activation |
| Reward distribution without reputation events | Post `ReputationChangeEvent`, never modify rep directly |
| Time-limited missions with no elapsed tracking | Update elapsed in system tick; check expiry each frame |
| Mission board showing locked faction missions | Filter by requiredStanding against player reputation |
