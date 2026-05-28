package com.galacticodyssey.mission.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestJournalHistoryTest {

    private QuestJournal journal;

    @BeforeEach
    void setUp() {
        journal = new QuestJournal();
    }

    @Test
    void completedQuests_initiallyEmpty() {
        assertTrue(journal.getCompletedQuests().isEmpty());
    }

    @Test
    void addCompleted_addsRecord() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job1", "Cargo Run", "CARGO_HAUL",
                QuestOutcome.COMPLETED, 1200, "Federation", 15f,
                System.currentTimeMillis());
        journal.addCompleted(record);

        assertEquals(1, journal.getCompletedQuests().size());
        assertEquals("job1", journal.getCompletedQuests().get(0).questId);
    }

    @Test
    void addCompleted_multipleRecords_newestFirst() {
        journal.addCompleted(new CompletedQuestRecord(
                "job1", "First", "CARGO_HAUL",
                QuestOutcome.COMPLETED, 100, null, 0, 1000L));
        journal.addCompleted(new CompletedQuestRecord(
                "job2", "Second", "BOUNTY_HUNT",
                QuestOutcome.FAILED, 0, null, -5f, 2000L));

        assertEquals(2, journal.getCompletedQuests().size());
        assertEquals("job2", journal.getCompletedQuests().get(0).questId);
        assertEquals("job1", journal.getCompletedQuests().get(1).questId);
    }
}
