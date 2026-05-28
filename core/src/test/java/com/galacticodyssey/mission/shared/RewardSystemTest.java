package com.galacticodyssey.mission.shared;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewardSystemTest {

    private EventBus eventBus;
    private RewardSystem rewardSystem;
    private final List<ReputationChangeEvent> repEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        rewardSystem = new RewardSystem(eventBus);
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);
    }

    @Test
    void questCompleted_publishesReputationChange() {
        MissionReward reward = new MissionReward();
        reward.reputationFaction = "explorers_guild";
        reward.reputationDelta = 10f;

        eventBus.publish(new QuestCompletedEvent("mission1", reward));

        assertEquals(1, repEvents.size());
        assertEquals("explorers_guild", repEvents.get(0).factionId);
        assertEquals(10f, repEvents.get(0).delta, 0.01f);
    }

    @Test
    void questCompleted_noReputationFaction_doesNotPublishRepEvent() {
        MissionReward reward = new MissionReward();
        reward.reputationFaction = null;

        eventBus.publish(new QuestCompletedEvent("mission2", reward));

        assertTrue(repEvents.isEmpty());
    }

    @Test
    void questCompleted_nullReward_doesNotThrow() {
        assertDoesNotThrow(() -> eventBus.publish(new QuestCompletedEvent("mission3", null)));
    }
}
