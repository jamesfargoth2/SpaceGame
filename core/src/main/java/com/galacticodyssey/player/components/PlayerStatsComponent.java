package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerStatsSnapshot;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.stats.SkillProgress;

public class PlayerStatsComponent implements Component, Snapshotable<PlayerStatsSnapshot> {

    public final ObjectMap<RealTimeSkill, SkillProgress> realTimeSkills = new ObjectMap<>();
    public final ObjectMap<PointSkill, Integer>          pointSkills    = new ObjectMap<>();

    public int   characterLevel   = 1;
    public float totalXP          = 0f;
    public int   unspentPoints    = 0;
    public int   unspentPerkPicks = 0;
    public final Array<String> perks = new Array<>();

    public PlayerStatsComponent() {
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            realTimeSkills.put(skill, new SkillProgress());
        }
        for (PointSkill skill : PointSkill.values()) {
            pointSkills.put(skill, 0);
        }
    }

    @Override
    public PlayerStatsSnapshot takeSnapshot() {
        PlayerStatsSnapshot s = new PlayerStatsSnapshot();
        s.characterLevel   = characterLevel;
        s.totalXP          = totalXP;
        s.unspentPoints    = unspentPoints;
        s.unspentPerkPicks = unspentPerkPicks;
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            SkillProgress p = realTimeSkills.get(skill);
            if (p == null) continue;
            s.realTimeLevels.put(skill.name(), p.level);
            s.realTimeXp.put(skill.name(), p.xp);
        }
        for (PointSkill skill : PointSkill.values()) {
            s.pointLevels.put(skill.name(), pointSkills.get(skill, 0));
        }
        s.perks = perks.toArray(String.class);
        return s;
    }

    @Override
    public void restoreFromSnapshot(PlayerStatsSnapshot s) {
        characterLevel   = s.characterLevel;
        totalXP          = s.totalXP;
        unspentPoints    = s.unspentPoints;
        unspentPerkPicks = s.unspentPerkPicks;
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            SkillProgress p = realTimeSkills.get(skill);
            Integer lvl = s.realTimeLevels.get(skill.name());
            Float   xp  = s.realTimeXp.get(skill.name());
            p.level = lvl != null ? lvl : 1;
            p.xp    = xp  != null ? xp  : 0f;
        }
        for (PointSkill skill : PointSkill.values()) {
            Integer lvl = s.pointLevels.get(skill.name());
            pointSkills.put(skill, lvl != null ? lvl : 0);
        }
        perks.clear();
        if (s.perks != null) perks.addAll(s.perks);
    }
}
