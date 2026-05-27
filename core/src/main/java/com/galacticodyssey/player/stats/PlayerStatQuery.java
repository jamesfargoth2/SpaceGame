package com.galacticodyssey.player.stats;

import com.galacticodyssey.player.components.PlayerStatsComponent;

public final class PlayerStatQuery {

    private PlayerStatQuery() {}

    /** Combined trade price multiplier from Trading + Diplomacy. <1.0 means better (cheaper buy/higher sell). */
    public static float getTradeModifier(PlayerStatsComponent stats) {
        int trading   = stats.realTimeSkills.get(RealTimeSkill.TRADING).level;
        int diplomacy = stats.pointSkills.get(PointSkill.DIPLOMACY, 0);
        return 1f - (trading * 0.002f + diplomacy * 0.003f);
    }

    /** Faction reputation gain multiplier from Diplomacy. */
    public static float getRepGainModifier(PlayerStatsComponent stats) {
        return 1f + stats.pointSkills.get(PointSkill.DIPLOMACY, 0) * 0.005f;
    }

    /** Maximum hireable crew from Leadership. */
    public static int getMaxCrewSize(PlayerStatsComponent stats) {
        return 4 + stats.pointSkills.get(PointSkill.LEADERSHIP, 0) / 10;
    }

    /** Crafting quality multiplier from Engineering. */
    public static float getCraftingQuality(PlayerStatsComponent stats) {
        return 1f + stats.pointSkills.get(PointSkill.ENGINEERING, 0) * 0.005f;
    }

    /** Flat hazard resistance (0–1) from Survival. */
    public static float getHazardResistance(PlayerStatsComponent stats) {
        return stats.pointSkills.get(PointSkill.SURVIVAL, 0) * 0.01f;
    }

    /** Crew XP rate multiplier from Leadership. */
    public static float getCrewXPMultiplier(PlayerStatsComponent stats) {
        return 1f + stats.pointSkills.get(PointSkill.LEADERSHIP, 0) * 0.004f;
    }

    /** Healing effectiveness multiplier from Medicine. */
    public static float getHealingEffectiveness(PlayerStatsComponent stats) {
        return 1f + stats.pointSkills.get(PointSkill.MEDICINE, 0) * 0.005f;
    }

    /** Scan quality multiplier from Science. */
    public static float getScanQuality(PlayerStatsComponent stats) {
        return 1f + stats.pointSkills.get(PointSkill.SCIENCE, 0) * 0.005f;
    }
}
