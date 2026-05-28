package com.galacticodyssey.mission.events;

import com.galacticodyssey.mission.shared.MissionReward;

public class QuestCompletedEvent {
    public final String missionId;
    public final MissionReward reward;

    public QuestCompletedEvent(String missionId, MissionReward reward) {
        this.missionId = missionId;
        this.reward = reward;
    }
}
