package com.galacticodyssey.mission.saga;

public class TriggerData {
    public String type;                     // "REPUTATION_THRESHOLD", "LOCATION_REACHED", "QUEST_COMPLETED"
    public String faction;
    public float minStanding;
    public String locationId;
    public String questId;
}
