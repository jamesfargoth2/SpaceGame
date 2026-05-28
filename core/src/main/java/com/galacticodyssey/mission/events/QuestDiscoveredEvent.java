package com.galacticodyssey.mission.events;

public final class QuestDiscoveredEvent {
    public final String missionId;
    public final String title;

    public QuestDiscoveredEvent(String missionId, String title) {
        this.missionId = missionId;
        this.title = title;
    }
}
