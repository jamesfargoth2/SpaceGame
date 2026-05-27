package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.events.CharacterLevelUpEvent;
import com.galacticodyssey.player.events.PerkAvailableEvent;
import com.galacticodyssey.player.events.SkillLevelUpEvent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.stats.SkillProgress;

public class RealTimeSkillSystem extends EntitySystem {

    public static final int PRIORITY = 25;
    public static final int MAX_SKILL_LEVEL = 100;

    private static final ComponentMapper<PlayerStatsComponent> STATS_M =
        ComponentMapper.getFor(PlayerStatsComponent.class);

    private final EventBus eventBus;

    public RealTimeSkillSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    /**
     * Award XP for a real-time skill action.
     *
     * @param difficultyMultiplier scales reward; 1.0 for normal actions, higher for harder ones
     */
    public void awardSkillXP(Entity player, RealTimeSkill skill, float baseXP, float difficultyMultiplier) {
        PlayerStatsComponent stats = STATS_M.get(player);
        if (stats == null) return;

        float xpGain = baseXP * difficultyMultiplier;
        SkillProgress progress = stats.realTimeSkills.get(skill);

        float threshold = xpThreshold(progress.level);
        while (progress.xp + xpGain >= threshold && progress.level < MAX_SKILL_LEVEL) {
            xpGain -= (threshold - progress.xp);
            progress.xp = 0f;
            progress.level++;
            threshold = xpThreshold(progress.level);
            eventBus.publish(new SkillLevelUpEvent(player, skill, progress.level));
        }
        progress.xp += xpGain;

        stats.totalXP += baseXP * difficultyMultiplier;
        checkCharacterLevelUp(player, stats);
    }

    /**
     * Spend an unspent point on a point-based skill. Returns false if no points available or
     * skill is already at the cap.
     */
    public boolean spendPoint(Entity player, com.galacticodyssey.player.stats.PointSkill skill) {
        PlayerStatsComponent stats = STATS_M.get(player);
        if (stats == null || stats.unspentPoints <= 0) return false;
        int current = stats.pointSkills.get(skill, 0);
        if (current >= MAX_SKILL_LEVEL) return false;
        stats.pointSkills.put(skill, current + 1);
        stats.unspentPoints--;
        return true;
    }

    @Override
    public void update(float deltaTime) {
        // XP is awarded via awardSkillXP() calls from other systems (combat, movement, etc.)
    }

    private void checkCharacterLevelUp(Entity player, PlayerStatsComponent stats) {
        int newLevel = 1 + (int) Math.sqrt(stats.totalXP / 250.0);
        while (stats.characterLevel < newLevel) {
            stats.characterLevel++;
            int points = (stats.characterLevel % 3 == 0) ? 3 : 2;
            stats.unspentPoints += points;
            eventBus.publish(new CharacterLevelUpEvent(player, stats.characterLevel, points));
            if (stats.characterLevel % 5 == 0) {
                eventBus.publish(new PerkAvailableEvent(player));
            }
        }
    }

    /** XP required to advance from currentLevel to currentLevel+1. */
    private static float xpThreshold(int currentLevel) {
        return 100f + currentLevel * currentLevel * 2f;
    }
}
