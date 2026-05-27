package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.stats.SkillProgress;

public class PlayerStatsComponent implements Component {

    public final ObjectMap<RealTimeSkill, SkillProgress> realTimeSkills = new ObjectMap<>();
    public final ObjectMap<PointSkill, Integer>          pointSkills    = new ObjectMap<>();

    public int   characterLevel = 1;
    public float totalXP        = 0f;
    public int   unspentPoints  = 0;
    public final Array<String> perks = new Array<>();

    public PlayerStatsComponent() {
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            realTimeSkills.put(skill, new SkillProgress());
        }
        for (PointSkill skill : PointSkill.values()) {
            pointSkills.put(skill, 0);
        }
    }
}
