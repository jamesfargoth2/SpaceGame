package com.galacticodyssey.player.stats;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.player.components.PlayerStatsComponent;

public final class PlayerStatQuery {

    private PlayerStatQuery() {}

    private static PerkRegistry perkRegistry;

    /** Set once at startup (GameWorld). Null disables perk folding (used by isolated tests). */
    public static void setPerkRegistry(PerkRegistry registry) { perkRegistry = registry; }

    private static float withPerks(PlayerStatsComponent stats, PerkTarget target, float base) {
        return perkRegistry == null ? base : perkRegistry.applyModifiers(stats, target, base);
    }

    /** Combined trade price multiplier from Trading + Diplomacy. <1.0 means better (cheaper buy/higher sell). */
    public static float getTradeModifier(PlayerStatsComponent stats) {
        int trading   = stats.realTimeSkills.get(RealTimeSkill.TRADING).level;
        int diplomacy = stats.pointSkills.get(PointSkill.DIPLOMACY, 0);
        float base = 1f - (trading * 0.002f + diplomacy * 0.003f);
        return withPerks(stats, PerkTarget.TRADE_PRICE, base);
    }

    /** Faction reputation gain multiplier from Diplomacy. */
    public static float getRepGainModifier(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.DIPLOMACY, 0) * 0.005f;
        return withPerks(stats, PerkTarget.REP_GAIN, base);
    }

    /** Maximum hireable crew from Leadership. */
    public static int getMaxCrewSize(PlayerStatsComponent stats) {
        int base = 4 + stats.pointSkills.get(PointSkill.LEADERSHIP, 0) / 10;
        return Math.round(withPerks(stats, PerkTarget.MAX_CREW, base));
    }

    /** Crafting quality multiplier from Engineering. */
    public static float getCraftingQuality(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.ENGINEERING, 0) * 0.005f;
        return withPerks(stats, PerkTarget.CRAFT_QUALITY, base);
    }

    /** Flat hazard resistance (0–1) from Survival. */
    public static float getHazardResistance(PlayerStatsComponent stats) {
        float base = stats.pointSkills.get(PointSkill.SURVIVAL, 0) * 0.01f;
        return withPerks(stats, PerkTarget.HAZARD_RESIST, base);
    }

    /** Crew XP rate multiplier from Leadership. */
    public static float getCrewXPMultiplier(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.LEADERSHIP, 0) * 0.004f;
        return withPerks(stats, PerkTarget.CREW_XP, base);
    }

    /** Healing effectiveness multiplier from Medicine. */
    public static float getHealingEffectiveness(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.MEDICINE, 0) * 0.005f;
        return withPerks(stats, PerkTarget.HEAL_EFF, base);
    }

    /** Scan quality multiplier from Science. */
    public static float getScanQuality(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.SCIENCE, 0) * 0.005f;
        return withPerks(stats, PerkTarget.SCAN_QUALITY, base);
    }

    /** Player outgoing-damage multiplier for the given damage type (1.0 if no perks). */
    public static float getOutgoingDamageMultiplier(PlayerStatsComponent stats, DamageType type) {
        PerkTarget target;
        switch (type) {
            case BALLISTIC: target = PerkTarget.DAMAGE_BALLISTIC; break;
            case ENERGY:
            case PLASMA:    target = PerkTarget.DAMAGE_ENERGY; break;
            case MELEE:     target = PerkTarget.DAMAGE_MELEE; break;
            default:        return 1f;
        }
        return withPerks(stats, target, 1f);
    }
}
