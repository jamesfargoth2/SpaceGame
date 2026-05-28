package com.galacticodyssey.mission.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletedQuestRecordTest {

    @Test
    void constructsWithAllFields() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job1", "Cargo Run: Medical Supplies", "CARGO_HAUL",
                QuestOutcome.COMPLETED, 1200, "Federation", 15f, 1716900000L);

        assertEquals("job1", record.questId);
        assertEquals("Cargo Run: Medical Supplies", record.questName);
        assertEquals("CARGO_HAUL", record.questType);
        assertEquals(QuestOutcome.COMPLETED, record.outcome);
        assertEquals(1200, record.creditsEarned);
        assertEquals("Federation", record.reputationFaction);
        assertEquals(15f, record.reputationDelta, 0.01f);
        assertEquals(1716900000L, record.timestampMs);
    }

    @Test
    void abandonedRecordHasNegativeReputation() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job2", "Bounty Hunt", "BOUNTY_HUNT",
                QuestOutcome.ABANDONED, 0, "Mercenary Guild", -10f, 1716900000L);

        assertEquals(QuestOutcome.ABANDONED, record.outcome);
        assertEquals(0, record.creditsEarned);
        assertTrue(record.reputationDelta < 0);
    }

    @Test
    void failedRecordHasNoCredits() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job3", "Escort Mission", "ESCORT",
                QuestOutcome.FAILED, 0, "Federation", -5f, 1716900000L);

        assertEquals(QuestOutcome.FAILED, record.outcome);
        assertEquals(0, record.creditsEarned);
    }
}
