package com.galacticodyssey.mission.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobBoardTest {

    private JobBoard board;
    private JobRegistry registry;
    private ProceduralJobGenerator generator;
    private SectorContext sector;

    @BeforeEach
    void setUp() {
        registry = new JobRegistry();
        generator = new ProceduralJobGenerator(registry);
        board = new JobBoard("station_alpha", registry, generator);
        sector = new SectorContext();
        sector.sectorId = "sec1";
        sector.locationIds = List.of("loc1", "loc2");
        sector.npcIds = List.of("npc1");
        sector.factionTags = List.of("trade_guilds");
        sector.dangerLevel = 0.2f;
    }

    @Test
    void refresh_populatesBoardUpToCap() {
        registry.register(makeBoardTemplate("t1", "trade_guilds", -100f));
        registry.register(makeBoardTemplate("t2", "trade_guilds", -100f));
        board.refresh(sector, 5, tag -> 50f);
        assertTrue(board.getAvailableJobs(tag -> 50f).size() <= 8);
        assertFalse(board.getAvailableJobs(tag -> 50f).isEmpty());
    }

    @Test
    void getAvailableJobs_filtersByStanding() {
        registry.register(makeBoardTemplate("low_req", "trade_guilds", -100f));
        registry.register(makeBoardTemplate("high_req", "trade_guilds", 75f));
        board.refresh(sector, 5, tag -> 50f);

        List<JobInstance> visible = board.getAvailableJobs(tag -> 50f);

        assertTrue(visible.stream().anyMatch(j -> "low_req".equals(j.templateId)));
        assertTrue(visible.stream().noneMatch(j -> "high_req".equals(j.templateId)));
    }

    @Test
    void acceptJob_movesToActive() {
        registry.register(makeBoardTemplate("t1", "trade_guilds", -100f));
        board.refresh(sector, 5, tag -> 0f);
        String jobId = board.getAvailableJobs(tag -> 0f).get(0).instanceId;

        JobInstance accepted = board.accept(jobId);

        assertNotNull(accepted);
        assertEquals(JobState.ACTIVE, accepted.state);
        assertTrue(board.getAvailableJobs(tag -> 0f).stream().noneMatch(j -> j.instanceId.equals(jobId)));
    }

    private JobTemplate makeBoardTemplate(String id, String faction, float requiredStanding) {
        JobTemplate t = new JobTemplate();
        t.id = id; t.type = JobType.CARGO_HAUL; t.giverFactionTag = faction;
        t.requiredStanding = requiredStanding; t.baseCredits = 500;
        t.discoveryMode = "BOARD";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DELIVER_CARGO;
        t.objectives.add(obj);
        return t;
    }
}
