package com.galacticodyssey.player.stats;

import com.galacticodyssey.player.components.PlayerStatsComponent;

public final class SkillCheck {

    private SkillCheck() {}

    /** Deterministic threshold check — passes when the point-skill level meets or exceeds required. */
    public static boolean check(PlayerStatsComponent stats, PointSkill skill, int required) {
        return stats.pointSkills.get(skill, 0) >= required;
    }

    /** Deterministic threshold check — passes when the real-time skill level meets or exceeds required. */
    public static boolean check(PlayerStatsComponent stats, RealTimeSkill skill, int required) {
        return stats.realTimeSkills.get(skill).level >= required;
    }
}
