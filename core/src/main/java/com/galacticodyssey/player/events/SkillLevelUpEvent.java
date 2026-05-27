package com.galacticodyssey.player.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.stats.RealTimeSkill;

public final class SkillLevelUpEvent {
    public final Entity player;
    public final RealTimeSkill skill;
    public final int newLevel;

    public SkillLevelUpEvent(Entity player, RealTimeSkill skill, int newLevel) {
        this.player   = player;
        this.skill    = skill;
        this.newLevel = newLevel;
    }
}
