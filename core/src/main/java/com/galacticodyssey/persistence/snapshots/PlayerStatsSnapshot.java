package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

/** Serializable snapshot of PlayerStatsComponent. Maps key on enum name strings. */
public class PlayerStatsSnapshot {
    public int   characterLevel;
    public float totalXP;
    public int   unspentPoints;
    public int   unspentPerkPicks;

    /** RealTimeSkill name -> level. */
    public Map<String, Integer> realTimeLevels = new HashMap<>();
    /** RealTimeSkill name -> in-level xp. */
    public Map<String, Float>   realTimeXp     = new HashMap<>();
    /** PointSkill name -> allocated level. */
    public Map<String, Integer> pointLevels    = new HashMap<>();

    public String[] perks = new String[0];

    public PlayerStatsSnapshot() {}
}
