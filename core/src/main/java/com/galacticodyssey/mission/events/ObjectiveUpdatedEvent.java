package com.galacticodyssey.mission.events;

public final class ObjectiveUpdatedEvent {
    public final String missionId;
    public final String objectiveId;
    public final int currentCount;
    public final int requiredCount;

    public ObjectiveUpdatedEvent(String missionId, String objectiveId, int currentCount, int requiredCount) {
        this.missionId = missionId;
        this.objectiveId = objectiveId;
        this.currentCount = currentCount;
        this.requiredCount = requiredCount;
    }
}
