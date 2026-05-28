package com.galacticodyssey.mission.shared;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;

public class RewardSystem {

    private final EventBus eventBus;

    public RewardSystem(EventBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(QuestCompletedEvent.class, this::onQuestCompleted);
    }

    private void onQuestCompleted(QuestCompletedEvent e) {
        if (e.reward == null) return;
        if (e.reward.reputationFaction != null && e.reward.reputationDelta != 0) {
            eventBus.publish(new ReputationChangeEvent(e.reward.reputationFaction, e.reward.reputationDelta, e.missionId));
        }
    }
}
