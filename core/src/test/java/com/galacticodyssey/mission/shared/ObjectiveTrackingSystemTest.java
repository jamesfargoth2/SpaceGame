package com.galacticodyssey.mission.shared;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.LocationEnteredEvent;
import com.galacticodyssey.mission.events.ObjectiveCompletedEvent;
import com.galacticodyssey.mission.events.ObjectiveUpdatedEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.saga.SagaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveTrackingSystemTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private ObjectiveTrackingSystem system;

    private final List<ObjectiveUpdatedEvent> updatedEvents = new ArrayList<>();
    private final List<ObjectiveCompletedEvent> completedEvents = new ArrayList<>();
    private final List<QuestCompletedEvent> questCompletedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        system = new ObjectiveTrackingSystem(eventBus, journal);

        eventBus.subscribe(ObjectiveUpdatedEvent.class, updatedEvents::add);
        eventBus.subscribe(ObjectiveCompletedEvent.class, completedEvents::add);
        eventBus.subscribe(QuestCompletedEvent.class, questCompletedEvents::add);
    }

    /** Helper: build a simple job with one DESTROY_TARGET objective. */
    private JobInstance makeDestroyJob(String instanceId, String targetId, int required) {
        JobInstance job = new JobInstance();
        job.instanceId = instanceId;
        job.state = JobState.ACTIVE;

        Objective obj = new Objective();
        obj.id = "obj-1";
        obj.type = ObjectiveType.DESTROY_TARGET;
        obj.targetId = targetId;
        obj.requiredCount = required;
        obj.currentCount = 0;
        obj.optional = false;
        obj.completed = false;
        job.objectives.add(obj);

        job.reward = new MissionReward();
        return job;
    }

    @Test
    void killMatchingTarget_incrementsCount() {
        JobInstance job = makeDestroyJob("job-1", "pirate-boss", 3);
        journal.addJob(job);

        system.onEntityKilled("pirate-boss");

        assertEquals(1, job.objectives.get(0).currentCount);
        assertEquals(1, updatedEvents.size());
        ObjectiveUpdatedEvent evt = updatedEvents.get(0);
        assertEquals("job-1", evt.missionId);
        assertEquals("obj-1", evt.objectiveId);
        assertEquals(1, evt.currentCount);
        assertEquals(3, evt.requiredCount);
        assertTrue(completedEvents.isEmpty(), "Objective should not be complete yet");
    }

    @Test
    void killNonMatchingTarget_doesNotIncrement() {
        JobInstance job = makeDestroyJob("job-2", "pirate-boss", 3);
        journal.addJob(job);

        system.onEntityKilled("random-civilian");

        assertEquals(0, job.objectives.get(0).currentCount);
        assertTrue(updatedEvents.isEmpty());
        assertTrue(completedEvents.isEmpty());
    }

    @Test
    void threeKillsOnThreeKillObjective_completesObjectiveAndQuest() {
        JobInstance job = makeDestroyJob("job-3", "pirate-boss", 3);
        journal.addJob(job);

        system.onEntityKilled("pirate-boss");
        system.onEntityKilled("pirate-boss");
        system.onEntityKilled("pirate-boss");

        assertTrue(job.objectives.get(0).completed, "Objective should be completed");
        assertEquals(JobState.COMPLETE, job.state, "Job should be COMPLETE");

        // Three ObjectiveUpdatedEvents, one ObjectiveCompletedEvent, one QuestCompletedEvent
        assertEquals(3, updatedEvents.size());
        assertEquals(1, completedEvents.size());
        assertEquals("job-3", completedEvents.get(0).missionId);
        assertEquals("obj-1", completedEvents.get(0).objectiveId);

        assertEquals(1, questCompletedEvents.size());
        assertEquals("job-3", questCompletedEvents.get(0).missionId);
    }

    @Test
    void locationEntered_matchingId_completesReachLocationObjective() {
        JobInstance job = new JobInstance();
        job.instanceId = "job-4";
        job.state = JobState.ACTIVE;

        Objective obj = new Objective();
        obj.id = "obj-loc";
        obj.type = ObjectiveType.REACH_LOCATION;
        obj.targetId = "station-alpha";
        obj.requiredCount = 1;
        obj.currentCount = 0;
        obj.optional = false;
        obj.completed = false;
        job.objectives.add(obj);
        job.reward = new MissionReward();

        journal.addJob(job);

        eventBus.publish(new LocationEnteredEvent("station-alpha"));

        assertTrue(obj.completed, "REACH_LOCATION objective should be completed");
        assertEquals(JobState.COMPLETE, job.state, "Job should be COMPLETE");
        assertEquals(1, completedEvents.size());
        assertEquals("job-4", completedEvents.get(0).missionId);
        assertEquals(1, questCompletedEvents.size());
        assertEquals("job-4", questCompletedEvents.get(0).missionId);
    }

    @Test
    void sagaObjective_incrementedOnMatchingEvent() {
        SagaInstance saga = new SagaInstance();
        saga.sagaDataId = "saga-1";
        saga.state = SagaState.ACTIVE;

        Objective obj = new Objective();
        obj.id = "obj-saga";
        obj.type = ObjectiveType.DESTROY_TARGET;
        obj.targetId = "bandit-leader";
        obj.requiredCount = 1;
        obj.currentCount = 0;
        obj.optional = false;
        obj.completed = false;
        saga.activeObjectives.add(obj);

        journal.setMainStory(saga);

        system.onEntityKilled("bandit-leader");

        assertTrue(obj.completed);
        assertEquals(1, completedEvents.size());
        assertEquals("saga-1", completedEvents.get(0).missionId);
    }

    @Test
    void surviveTimeObjective_completesAfterEnoughDeltaTime() {
        JobInstance job = new JobInstance();
        job.instanceId = "job-timer";
        job.state = JobState.ACTIVE;

        Objective obj = new Objective();
        obj.id = "obj-timer";
        obj.type = ObjectiveType.SURVIVE_TIME;
        obj.targetId = "";
        obj.requiredCount = 10;
        obj.currentCount = 0;
        obj.optional = false;
        obj.completed = false;
        job.objectives.add(obj);
        job.reward = new MissionReward();

        journal.addJob(job);

        system.update(5.0f);
        assertFalse(obj.completed);

        system.update(5.0f);
        assertTrue(obj.completed);
        assertEquals(JobState.COMPLETE, job.state);
        assertEquals(1, questCompletedEvents.size());
    }
}
