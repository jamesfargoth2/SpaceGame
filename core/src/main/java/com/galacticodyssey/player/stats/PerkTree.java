package com.galacticodyssey.player.stats;

import com.badlogic.gdx.utils.Array;

/** All perk nodes anchored to one real-time skill, ordered by tier. */
public class PerkTree {
    public final RealTimeSkill skill;
    public final Array<PerkNodeDef> nodes = new Array<>();

    public PerkTree(RealTimeSkill skill) {
        this.skill = skill;
    }
}
