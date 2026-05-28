package com.galacticodyssey.mission.job;

import com.galacticodyssey.mission.shared.MissionReward;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.discovery.DiscoveryLead;

import java.util.ArrayList;
import java.util.List;

public class JobInstance {
    public String instanceId;
    public String templateId;
    public String displayName;
    public String displayDescription;
    public JobType type;
    public JobState state = JobState.AVAILABLE;
    public String giverNpcId;
    public String giverLocationId;
    public String triggeringEventId;    // non-null for EVENT_DRIVEN jobs
    public float difficulty;
    public float timeLimit;             // seconds; 0 = no limit
    public float elapsed;
    public List<Objective> objectives = new ArrayList<>();
    public MissionReward reward;
    public DiscoveryLead lead;          // null for BOARD jobs

    public boolean allRequiredComplete() {
        return objectives.stream().filter(o -> !o.optional).allMatch(o -> o.completed);
    }
}
