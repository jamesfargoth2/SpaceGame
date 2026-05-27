---
name: libgdx-player-rpg-stats
description: >
  Enforces correct player RPG progression including dual-track skill leveling
  (use-based real-time skills and point-allocated skills), character leveling,
  perk selection, and stat modifier application for a libGDX 3D space game
  using Ashley ECS. Use this skill whenever writing or modifying: player skill
  definitions, XP-by-use tracking, character level calculation, skill point
  allocation, perk trees, stat modifiers that affect other systems (combat
  accuracy, crafting quality, crew leadership, trade prices, diplomacy bonuses),
  or the leveling UI. Also triggers when adding new skills, balancing XP curves,
  or integrating stat checks into dialogue, missions, or gameplay systems.
---

# libGDX Player RPG Stats & Leveling

## Dual-Track Skill Architecture

**Track A — Real-Time Skills** (improve by doing, Skyrim-style):
Skills that level passively as the player performs related actions.

**Track B — Point-Based Skills** (spend points on level-up, Fallout-style):
Skills allocated from a finite point pool earned at each character level.

```
Real-Time Skill XP -> aggregated -> Character Level Up
                                      |- +2-3 Skill Points (allocated to Track B)
                                      +- every 5 levels: Perk selection
```

## Data Model

```java
public class PlayerStatsComponent implements Component {
    public ObjectMap<RealTimeSkill, SkillProgress> realTimeSkills = new ObjectMap<>();
    public ObjectMap<PointSkill, Integer> pointSkills = new ObjectMap<>();
    public int   characterLevel  = 1;
    public float totalXP         = 0f;
    public int   unspentPoints   = 0;
    public Array<String> perks   = new Array<>();
}

public class SkillProgress {
    public int   level = 1;
    public float xp    = 0f;
}
```

## Real-Time Skills

| Skill | Leveled By | Gameplay Effect |
|---|---|---|
| Firearms | Shooting with ballistic weapons | Ballistic damage, reload speed |
| Energy Weapons | Shooting with energy weapons | Energy damage, heat management |
| Melee | Landing melee hits | Melee damage, block effectiveness |
| Piloting | Flying ships, combat maneuvers | Ship handling, max G tolerance |
| Athletics | Sprinting, jumping, climbing | Sprint duration, jump height, stamina |
| Stealth | Remaining undetected near enemies | Detection range reduction |
| Trading | Completing buy/sell transactions | Better base prices |
| Mining | Extracting resources | Extraction speed, rare material chance |
| Repair | Fixing ship components | Repair speed, health restored |

```java
public class RealTimeSkillSystem extends EntitySystem {
    public void awardSkillXP(Entity player, RealTimeSkill skill, float baseXP,
                              float difficultyMultiplier) {
        PlayerStatsComponent stats = Mappers.playerStats.get(player);
        SkillProgress progress = stats.realTimeSkills.get(skill);
        float xpGain = baseXP * difficultyMultiplier;
        progress.xp += xpGain;
        float threshold = getXPThreshold(progress.level);
        while (progress.xp >= threshold && progress.level < 100) {
            progress.xp -= threshold;
            progress.level++;
            threshold = getXPThreshold(progress.level);
            EventBus.post(new SkillLevelUpEvent(player, skill, progress.level));
        }
        stats.totalXP += xpGain;
        checkCharacterLevelUp(player, stats);
    }

    private float getXPThreshold(int currentLevel) {
        return 100f + currentLevel * currentLevel * 2f;
    }
}
```

## Character Leveling

```java
private void checkCharacterLevelUp(Entity player, PlayerStatsComponent stats) {
    int newLevel = 1 + (int)(Math.sqrt(stats.totalXP / 250.0));
    while (stats.characterLevel < newLevel) {
        stats.characterLevel++;
        int points = (stats.characterLevel % 3 == 0) ? 3 : 2;
        stats.unspentPoints += points;
        EventBus.post(new CharacterLevelUpEvent(player, stats.characterLevel, points));
        if (stats.characterLevel % 5 == 0) EventBus.post(new PerkAvailableEvent(player));
    }
}
```

## Point-Based Skills

| Skill | Effect | Applied Where |
|---|---|---|
| Medicine | Healing effectiveness, medical crafting | Crew health, medical items |
| Hacking | Bypass security, access locked data | Locked doors, encrypted terminals |
| Engineering | Advanced ship mods, crafting quality | Crafting system, ship upgrades |
| Leadership | Max crew size, crew XP rate, morale | Crew system |
| Diplomacy | Better prices, dialogue options, rep gains | Faction, dialogue, economy |
| Science | Scan efficiency, anomaly analysis | Scanning, rare phenomena, crafting |
| Tactics | Squad command, boarding success | Combat, crew autonomous behavior |
| Survival | Hazard resistance, food/O2 efficiency | Planet exploration, life support |

Point allocation is permanent — no free respecs.

## Stat Modifier API

```java
public class PlayerStatQuery {
    public static float getTradeModifier(PlayerStatsComponent stats) {
        int trading = stats.realTimeSkills.get(RealTimeSkill.TRADING).level;
        int diplomacy = stats.pointSkills.get(PointSkill.DIPLOMACY, 0);
        return 1f - (trading * 0.002f + diplomacy * 0.003f);
    }
    public static float getRepGainModifier(PlayerStatsComponent stats) {
        return 1f + stats.pointSkills.get(PointSkill.DIPLOMACY, 0) * 0.005f;
    }
    public static int getMaxCrewSize(PlayerStatsComponent stats) {
        return 4 + stats.pointSkills.get(PointSkill.LEADERSHIP, 0) / 10;
    }
    public static float getCraftingQuality(PlayerStatsComponent stats) {
        return 1f + stats.pointSkills.get(PointSkill.ENGINEERING, 0) * 0.005f;
    }
    public static float getHazardResistance(PlayerStatsComponent stats) {
        return stats.pointSkills.get(PointSkill.SURVIVAL, 0) * 0.01f;
    }
}
```

## Skill Checks

Deterministic — threshold pass/fail, no RNG:

```java
public class SkillCheck {
    public static boolean check(PlayerStatsComponent stats, PointSkill skill, int required) {
        return stats.pointSkills.get(skill, 0) >= required;
    }
    public static boolean check(PlayerStatsComponent stats, RealTimeSkill skill, int required) {
        return stats.realTimeSkills.get(skill).level >= required;
    }
}
```

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| XP threshold formula | 100 + level^2 * 2 | Per-skill XP to next level |
| Char level formula | 1 + sqrt(totalXP / 250) | Total XP to character level |
| Points per level | 2 (3 every 3rd level) | Skill point budget |
| Perk interval | Every 5 character levels | Perk selection frequency |
| Max skill level | 100 | Cap for both tracks |

## Common Mistakes

| Mistake | Fix |
|---|---|
| XP gain without difficulty scaling | Scale XP by action difficulty |
| Directly reading stats from component | Use PlayerStatQuery methods |
| Character level based on highest single skill | Derives from total XP across ALL real-time skills |
| Perk prerequisites ignored | Always check skill requirements before selection |
| Forgetting to post events on level-up | Always post SkillLevelUpEvent and CharacterLevelUpEvent |
| Point allocation allowing respecs | Allocation is permanent; enforce in UI and API |
