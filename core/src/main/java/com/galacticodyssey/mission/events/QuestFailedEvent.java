package com.galacticodyssey.mission.events;

public final class QuestFailedEvent {
    public final String missionId;
    public final String reason;

    public QuestFailedEvent(String missionId, String reason) {
        this.missionId = missionId;
        this.reason = reason;
    }
}
