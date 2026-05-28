package com.galacticodyssey.mission.shared;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.events.QuestFailedEvent;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.job.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QuestAbandonmentTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private ObjectiveTrackingSystem system;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        system = new ObjectiveTrackingSystem(eventBus, journal);
    }

    @Test
    void abandonQuest_removesFromActiveJobs() {
        JobInstance job = makeJob("job1", "Cargo Run", "CARGO_HAUL");
        journal.addJob(job);
        assertEquals(1, journal.getActiveJobs().size());

        eventBus.publish(new QuestAbandonedEvent("job1"));

        assertEquals(0, journal.getActiveJobs().size());
    }

    @Test
    void abandonQuest_addsToHistory() {
        JobInstance job = makeJob("job1", "Cargo Run", "CARGO_HAUL");
        job.reward = new MissionReward();
        job.reward.reputationFaction = "Federation";
        job.reward.reputationDelta = 15f;
        journal.addJob(job);

        eventBus.publish(new QuestAbandonedEvent("job1"));

        assertEquals(1, journal.getCompletedQuests().size());
        CompletedQuestRecord record = journal.getCompletedQuests().get(0);
        assertEquals("job1", record.questId);
        assertEquals(QuestOutcome.ABANDONED, record.outcome);
        assertEquals(0, record.creditsEarned);
        assertTrue(record.reputationDelta < 0);
    }

    @Test
    void abandonQuest_publishesQuestFailedEvent() {
        JobInstance job = makeJob("job1", "Cargo Run", "CARGO_HAUL");
        job.reward = new MissionReward();
        job.reward.reputationFaction = "Federation";
        job.reward.reputationDelta = 15f;
        journal.addJob(job);

        AtomicReference<QuestFailedEvent> captured = new AtomicReference<>();
        eventBus.subscribe(QuestFailedEvent.class, captured::set);

        eventBus.publish(new QuestAbandonedEvent("job1"));

        assertNotNull(captured.get());
        assertEquals("job1", captured.get().missionId);
    }

    @Test
    void abandonQuest_nonexistentId_noOp() {
        eventBus.publish(new QuestAbandonedEvent("nonexistent"));
        assertEquals(0, journal.getCompletedQuests().size());
    }

    private JobInstance makeJob(String id, String name, String type) {
        JobInstance j = new JobInstance();
        j.instanceId = id;
        j.displayName = name;
        j.type = JobType.valueOf(type);
        j.state = JobState.ACTIVE;
        j.reward = new MissionReward();
        return j;
    }
}
