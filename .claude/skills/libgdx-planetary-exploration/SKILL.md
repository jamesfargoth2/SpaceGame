---
name: libgdx-planetary-exploration
description: >
  Enforces correct planetary surface exploration architecture including
  three-tier settlement hierarchy (cities, villages, wilderness), biome-driven
  terrain, environmental hazards, NPC population with schedules, resource
  deposit placement, and scanning mechanics for a libGDX 3D space game using
  Ashley ECS. Use this skill whenever writing or modifying: city district
  layout, settlement generation, wilderness content placement (ruins, caves,
  deposits), biome-specific hazards (radiation, toxic, temperature), day/night
  cycles, NPC pathfinding on terrain, planetary scanning/discovery systems,
  or creature/flora spawning. Also triggers when adding new settlement types,
  environmental hazard zones, or planet-side quest content.
---

# libGDX Planetary Exploration

## Three Settlement Tiers

| Tier | Size | Generation | NPC Count | Services |
|---|---|---|---|---|
| Large City | Multiple districts | Handcrafted landmarks + procedural fill | 50-200 | Full |
| Small Settlement | Single cluster | Procedural, biome-appropriate | 5-15 | Basic |
| Wilderness | Unbounded terrain | Fully procedural | 0 (creatures only) | None |

## City District Layout

```java
public class CityData {
    public String id;
    public String name;
    public String controllingFaction;
    public Array<DistrictData> districts = new Array<>();
}

public enum DistrictType {
    SPACEPORT, COMMERCIAL, RESIDENTIAL, INDUSTRIAL, GOVERNMENT, SLUMS
}
```

Landmarks hand-placed, surrounding blocks procedurally filled. Key interiors: SHOP, CANTINA, FACTION_HQ, QUEST_GIVER, WORKSHOP, MEDICAL_BAY.

## Small Settlements

Every settlement needs at minimum one trader and one quest giver. Procedurally placed with biome-appropriate architecture, 5-15 NPCs.

## Wilderness Content

| Content | Placement Rule | Purpose |
|---|---|---|
| Resource Deposits | Density by biome + rarity tier | Mining, gathering |
| Ruins | Low density, near anomalies | Lore, loot, quest triggers |
| Caves | Terrain-driven (mountains, cliffs) | Dungeons, creature habitats |
| Creature Habitats | Biome-specific density | Combat, biological harvesting |

Use chunk position + planet seed for deterministic scatter.

## Environmental Hazards

```java
public class EnvironmentComponent implements Component {
    public float radiation;     // 0-1, requires rad suit above 0.3
    public float toxicity;      // 0-1, requires hazmat above 0.3
    public float temperature;   // Kelvin, extremes require thermal gear
    public float gravity;       // multiplier
    public boolean hasAtmosphere;
}
```

Survival skill + gear provide resistance. Damage applies when lacking protection.

## Day/Night Cycle

0-1 scale (0=midnight, 0.25=dawn, 0.5=noon, 0.75=dusk). Configurable day length (default 1200s). Affects NPC schedules and lighting.

## NPC Schedules

```java
public class ScheduleEntry {
    public float startTime, endTime;
    public String locationId;
    public NPCActivity activity; // WORK, SLEEP, PATROL, WANDER, SOCIALIZE
}
```

Schedule entries must cover the full 0-1 time range.

## Scanning / Discovery

Science skill increases scanner effective range and unlocks higher-tier discoveries. Some discoveries require minimum Science level.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| City district count | 4-6 | Districts per large city |
| Settlement NPC range | 5-15 | NPCs per small settlement |
| Deposit density | 2-5 common, 0-1 rare per chunk | Resource availability |
| Day cycle length | 1200s (20 min) | Real-time day length |
| Scanner base radius | 100m | Discovery range |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Settlements without trader/quest giver | Every settlement needs minimum services |
| Hazard zones with no UI warning | Post warnings on entering hazardous areas |
| NPC schedules not covering full day | Entries must span full 0-1 range |
| Wilderness content not seeded | Use chunk + planet seed for determinism |
| Scanning without Science check | Some discoveries require minimum Science |
| Interiors loading synchronously | Load async with transition animation |
