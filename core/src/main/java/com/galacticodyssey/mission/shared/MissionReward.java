package com.galacticodyssey.mission.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class MissionReward {
    public int credits;
    public Map<String, Integer> resources = new HashMap<>();
    public String reputationFaction;
    public float reputationDelta;
    public float crewXP;
    public List<String> itemRewards = new ArrayList<>();

    public MissionReward scaled(float multiplier) {
        MissionReward r = new MissionReward();
        r.credits = Math.round(credits * multiplier);
        r.reputationFaction = reputationFaction;
        r.reputationDelta = reputationDelta * multiplier;
        r.crewXP = crewXP * multiplier;
        r.itemRewards = new ArrayList<>(itemRewards);
        for (Map.Entry<String, Integer> e : resources.entrySet())
            r.resources.put(e.getKey(), Math.round(e.getValue() * multiplier));
        return r;
    }
}
