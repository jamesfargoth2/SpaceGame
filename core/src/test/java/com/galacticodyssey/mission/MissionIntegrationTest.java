package com.galacticodyssey.mission;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.FactionWarStartedEvent;
import com.galacticodyssey.core.events.LocationEnteredEvent;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.discovery.QuestDiscoverySystem;
import com.galacticodyssey.mission.events.QuestCompletedEvent;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import com.galacticodyssey.mission.job.*;
import com.galacticodyssey.mission.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissionIntegrationTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private JobRegistry registry;
    private ProceduralJobGenerator procGen;
    private EventJobGenerator eventJobGen;
    private QuestDiscoverySystem discovery;
    private ObjectiveTrackingSystem tracking;
    private RewardSystem rewards;

    private final List<QuestDiscoveredEvent> discovered = new ArrayList<>();
    private final List<QuestCompletedEvent> completed = new ArrayList<>();
    private final List<ReputationChangeEvent> repEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        registry = new JobRegistry();
        procGen = new ProceduralJobGenerator(registry);
        eventJobGen = new EventJobGenerator(eventBus, registry, procGen);
        discovery = new QuestDiscoverySystem(eventBus, journal);
        tracking = new ObjectiveTrackingSystem(eventBus, journal);
        rewards = new RewardSystem(eventBus);

        registry.register(makeMercenaryTemplate());

        eventBus.subscribe(QuestDiscoveredEvent.class, discovered::add);
        eventBus.subscribe(QuestCompletedEvent.class, completed::add);
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);

        eventJobGen.setJobListener(job -> {
            if (job.lead != null) discovery.registerLead(job, job.lead);
            journal.addRumour(job);
        });
    }

    @Test
    void fullPipeline_warEventToQuestComplete() {
        // World event fires — mercenary job spawns as RUMOURED
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        assertEquals(1, journal.getRumourBoard().size());
        assertEquals(JobState.RUMOURED, journal.getRumourBoard().get(0).state);

        // Player visits the location from the lead — job activates cold
        String locationId = journal.getRumourBoard().get(0).lead.locationId;
        eventBus.publish(new LocationEnteredEvent(locationId));
        assertEquals(1, discovered.size());
        assertEquals(1, journal.getActiveJobs().size());
        JobInstance job = journal.getActiveJobs().get(0);
        assertEquals(JobState.ACTIVE, job.state);

        // Player completes the objective (5 kills)
        String targetId = job.objectives.get(0).targetId;
        for (int i = 0; i < 5; i++) tracking.onEntityKilled(targetId);

        assertEquals(1, completed.size());
        assertEquals(job.instanceId, completed.get(0).missionId);
        assertFalse(repEvents.isEmpty());
        assertEquals("military", repEvents.get(0).factionId);
    }

    private JobTemplate makeMercenaryTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "mercenary_war"; t.type = JobType.MERCENARY;
        t.giverFactionTag = "military"; t.baseCredits = 2000;
        t.baseReputationDelta = 10f;
        t.discoveryMode = "EVENT_DRIVEN";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET;
        obj.requiredCount = 5;
        t.objectives.add(obj);
        return t;
    }
}
