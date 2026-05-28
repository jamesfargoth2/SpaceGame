package com.galacticodyssey.mission.job;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventJobGeneratorTest {

    private EventBus eventBus;
    private JobRegistry registry;
    private ProceduralJobGenerator generator;
    private EventJobGenerator eventJobGen;
    private final List<JobInstance> spawnedJobs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new JobRegistry();
        registry.register(makeMercenaryTemplate());
        registry.register(makeSalvageTemplate());
        generator = new ProceduralJobGenerator(registry);
        eventJobGen = new EventJobGenerator(eventBus, registry, generator);
        eventJobGen.setJobListener(spawnedJobs::add);
    }

    @Test
    void factionWarStarted_spawnsMercenaryJob() {
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        assertEquals(1, spawnedJobs.size());
        assertEquals(JobType.MERCENARY, spawnedJobs.get(0).type);
        assertEquals(JobState.RUMOURED, spawnedJobs.get(0).state);
        assertEquals("war1", spawnedJobs.get(0).triggeringEventId);
    }

    @Test
    void factionWarEnded_expiresPendingRumouredJobs() {
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        JobInstance job = spawnedJobs.get(0);
        assertEquals(JobState.RUMOURED, job.state);

        eventBus.publish(new FactionWarEndedEvent("war1"));
        assertEquals(JobState.EXPIRED, job.state);
    }

    @Test
    void factionWarEnded_doesNotExpireActiveJobs() {
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        spawnedJobs.get(0).state = JobState.ACTIVE;

        eventBus.publish(new FactionWarEndedEvent("war1"));
        assertEquals(JobState.ACTIVE, spawnedJobs.get(0).state);
    }

    @Test
    void shipMissing_spawnsSalvageJob() {
        eventBus.publish(new ShipMissingEvent("ship_47", "location_debris_field"));
        assertEquals(1, spawnedJobs.size());
        assertEquals(JobType.SALVAGE, spawnedJobs.get(0).type);
    }

    private JobTemplate makeMercenaryTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "mercenary_war"; t.type = JobType.MERCENARY;
        t.giverFactionTag = "military"; t.baseCredits = 2000;
        t.discoveryMode = "EVENT_DRIVEN";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET;
        obj.requiredCount = 5;
        t.objectives.add(obj);
        return t;
    }

    private JobTemplate makeSalvageTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "salvage_ship"; t.type = JobType.SALVAGE;
        t.giverFactionTag = "trade_guilds"; t.baseCredits = 1500;
        t.discoveryMode = "EVENT_DRIVEN";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.REACH_LOCATION;
        t.objectives.add(obj);
        return t;
    }
}
