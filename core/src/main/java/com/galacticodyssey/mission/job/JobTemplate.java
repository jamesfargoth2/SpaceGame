package com.galacticodyssey.mission.job;

import java.util.ArrayList;
import java.util.List;

public class JobTemplate {
    public String id;
    public JobType type;
    public String giverFactionTag;
    public float requiredStanding = -100f;
    public String discoveryMode = "BOARD";  // BOARD | EVENT_DRIVEN | BOTH
    public int baseCredits;
    public float baseReputationDelta;
    public float baseTimeLimitSeconds = 0;  // 0 = no time limit
    public List<ObjectiveTemplate> objectives = new ArrayList<>();
}
