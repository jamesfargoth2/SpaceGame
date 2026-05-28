package com.galacticodyssey.mission.events;

public class ObjectiveCompletedEvent {
    public final String missionId;
    public final String objectiveId;

    public ObjectiveCompletedEvent(String missionId, String objectiveId) {
        this.missionId = missionId;
        this.objectiveId = objectiveId;
    }
}
