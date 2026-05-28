package com.galacticodyssey.mission.job;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.shared.QuestJournal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobAcceptanceTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private JobBoard board;
    private JobRegistry registry;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        registry = new JobRegistry();

        JobTemplate template = new JobTemplate();
        template.id = "cargo_haul_legal";
        template.name = "Cargo Hauling";
        template.type = JobType.CARGO_HAUL;
        template.discoveryMode = "BOARD";
        template.baseCredits = 1000;
        registry.register(template);

        board = new JobBoard("station1", registry, null);
    }

    @Test
    void acceptJob_movesFromBoardToJournal() {
        JobInstance job = new JobInstance();
        job.instanceId = "job1";
        job.templateId = "cargo_haul_legal";
        job.displayName = "Cargo Run: Medical Supplies";
        job.type = JobType.CARGO_HAUL;
        job.state = JobState.AVAILABLE;

        assertTrue(journal.addJob(job));
        assertEquals(1, journal.getActiveJobs().size());
    }

    @Test
    void acceptJob_atMaxCap_fails() {
        for (int i = 0; i < 10; i++) {
            JobInstance job = new JobInstance();
            job.instanceId = "job" + i;
            job.state = JobState.ACTIVE;
            journal.addJob(job);
        }

        JobInstance overflow = new JobInstance();
        overflow.instanceId = "overflow";
        overflow.state = JobState.AVAILABLE;
        assertFalse(journal.addJob(overflow));
    }
}
