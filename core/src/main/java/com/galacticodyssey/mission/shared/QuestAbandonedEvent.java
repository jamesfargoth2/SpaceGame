package com.galacticodyssey.mission.shared;

public final class QuestAbandonedEvent {
    public final String questInstanceId;

    public QuestAbandonedEvent(String questInstanceId) {
        this.questInstanceId = questInstanceId;
    }
}
