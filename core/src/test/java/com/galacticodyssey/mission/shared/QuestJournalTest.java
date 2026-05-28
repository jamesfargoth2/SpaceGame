package com.galacticodyssey.mission.shared;

import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.saga.SagaInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestJournalTest {

    private QuestJournal journal;

    @BeforeEach
    void setUp() { journal = new QuestJournal(); }

    @Test
    void addJob_underCap_succeeds() {
        JobInstance job = makeJob("job1");
        assertTrue(journal.addJob(job));
        assertEquals(1, journal.getActiveJobs().size());
    }

    @Test
    void addJob_atCap_returnsFalse() {
        for (int i = 0; i < 10; i++) journal.addJob(makeJob("job" + i));
        assertFalse(journal.addJob(makeJob("overflow")));
    }

    @Test
    void findJob_byId_returnsCorrectInstance() {
        JobInstance job = makeJob("abc");
        journal.addJob(job);
        assertSame(job, journal.findJob("abc"));
    }

    @Test
    void findJob_missingId_returnsNull() {
        assertNull(journal.findJob("nonexistent"));
    }

    @Test
    void removeJob_removesFromActive() {
        JobInstance job = makeJob("job1");
        journal.addJob(job);
        journal.removeJob("job1");
        assertTrue(journal.getActiveJobs().isEmpty());
    }

    @Test
    void addRumour_andRetrieve() {
        JobInstance job = makeJob("rumour1");
        journal.addRumour(job);
        assertEquals(1, journal.getRumourBoard().size());
    }

    @Test
    void promoteRumour_movesToActive() {
        JobInstance job = makeJob("r1");
        journal.addRumour(job);
        assertTrue(journal.promoteRumour("r1"));
        assertTrue(journal.getRumourBoard().isEmpty());
        assertEquals(1, journal.getActiveJobs().size());
    }

    private JobInstance makeJob(String id) {
        JobInstance j = new JobInstance();
        j.instanceId = id;
        j.state = JobState.AVAILABLE;
        return j;
    }
}
