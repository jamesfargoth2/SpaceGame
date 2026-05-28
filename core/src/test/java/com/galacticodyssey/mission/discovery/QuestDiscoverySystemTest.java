package com.galacticodyssey.mission.discovery;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.LocationEnteredEvent;
import com.galacticodyssey.core.events.NpcDialogueEvent;
import com.galacticodyssey.core.events.ScanCompleteEvent;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.shared.MissionReward;
import com.galacticodyssey.mission.shared.QuestJournal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestDiscoverySystemTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private QuestDiscoverySystem discovery;
    private final List<QuestDiscoveredEvent> discovered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        discovery = new QuestDiscoverySystem(eventBus, journal);
        eventBus.subscribe(QuestDiscoveredEvent.class, discovered::add);
    }

    @Test
    void npcRumour_activatesMatchingJob() {
        JobInstance job = makeRumouredJob("j1", List.of("npc_bartender"), "station_alpha");
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new NpcDialogueEvent("npc_bartender", "RUMOUR"));

        assertEquals(1, discovered.size());
        assertEquals("j1", discovered.get(0).missionId);
        assertEquals(JobState.ACTIVE, job.state);
        assertTrue(journal.getActiveJobs().contains(job));
        assertTrue(journal.getRumourBoard().isEmpty());
    }

    @Test
    void locationEntered_activatesJobCold() {
        JobInstance job = makeRumouredJob("j2", List.of("npc_far_away"), "derelict_station");
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new LocationEnteredEvent("derelict_station"));

        assertEquals(1, discovered.size());
        assertEquals(JobState.ACTIVE, job.state);
    }

    @Test
    void npcDialogue_nonRumourTopic_doesNotActivate() {
        JobInstance job = makeRumouredJob("j3", List.of("npc_trader"), "loc1");
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new NpcDialogueEvent("npc_trader", "TRADE"));

        assertTrue(discovered.isEmpty());
        assertEquals(JobState.RUMOURED, job.state);
    }

    @Test
    void scanComplete_activatesMatchingJob() {
        JobInstance job = makeRumouredJob("j4", List.of(), "anomaly_k7");
        job.lead.locationId = "anomaly_k7";
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new ScanCompleteEvent("anomaly_k7"));

        assertEquals(JobState.ACTIVE, job.state);
    }

    private JobInstance makeRumouredJob(String id, List<String> npcIds, String locationId) {
        JobInstance job = new JobInstance();
        job.instanceId = id;
        job.state = JobState.RUMOURED;
        job.reward = new MissionReward();
        DiscoveryLead lead = new DiscoveryLead();
        lead.jobInstanceId = id;
        lead.rumourNpcIds = npcIds;
        lead.locationId = locationId;
        job.lead = lead;
        return job;
    }
}
