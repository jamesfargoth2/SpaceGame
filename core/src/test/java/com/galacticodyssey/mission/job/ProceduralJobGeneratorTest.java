package com.galacticodyssey.mission.job;

import com.galacticodyssey.mission.shared.Objective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProceduralJobGeneratorTest {

    private JobRegistry registry;
    private ProceduralJobGenerator generator;
    private SectorContext sector;

    @BeforeEach
    void setUp() {
        registry = new JobRegistry();
        generator = new ProceduralJobGenerator(registry);
        sector = new SectorContext();
        sector.sectorId = "sector_alpha";
        sector.locationIds = List.of("station_1", "outpost_7");
        sector.npcIds = List.of("npc_bartender", "npc_guard");
        sector.factionTags = List.of("trade_guilds");
        sector.dangerLevel = 0.3f;
    }

    @Test
    void generate_scalesDifficultyWithPlayerLevel() {
        JobTemplate template = makeBountyTemplate();
        registry.register(template);
        ReputationQuery rep = tag -> 50f;

        JobInstance job5 = generator.generate(template, sector, 5, rep);
        JobInstance job10 = generator.generate(template, sector, 10, rep);

        assertTrue(job10.difficulty > job5.difficulty);
    }

    @Test
    void generate_scalesCreditsWithPlayerLevel() {
        JobTemplate template = makeBountyTemplate();
        registry.register(template);
        ReputationQuery rep = tag -> 50f;

        JobInstance low = generator.generate(template, sector, 1, rep);
        JobInstance high = generator.generate(template, sector, 10, rep);

        assertTrue(high.reward.credits > low.reward.credits);
    }

    @Test
    void generate_populatesObjectivesFromTemplate() {
        JobTemplate template = makeBountyTemplate();
        registry.register(template);

        JobInstance job = generator.generate(template, sector, 5, tag -> 0f);

        assertEquals(1, job.objectives.size());
        Objective obj = job.objectives.get(0);
        assertEquals(com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET, obj.type);
        assertNotNull(obj.id);
    }

    @Test
    void generate_startsInAvailableState() {
        JobInstance job = generator.generate(makeBountyTemplate(), sector, 1, tag -> 0f);
        assertEquals(JobState.AVAILABLE, job.state);
    }

    @Test
    void generate_boardJob_hasNullLead() {
        JobTemplate template = makeBountyTemplate();
        template.discoveryMode = "BOARD";
        JobInstance job = generator.generate(template, sector, 1, tag -> 0f);
        assertNull(job.lead);
    }

    private JobTemplate makeBountyTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "bounty_test";
        t.type = JobType.BOUNTY_HUNT;
        t.giverFactionTag = "trade_guilds";
        t.baseCredits = 1000;
        t.baseReputationDelta = 5f;
        t.discoveryMode = "BOARD";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET;
        obj.requiredCount = 1;
        t.objectives.add(obj);
        return t;
    }
}
