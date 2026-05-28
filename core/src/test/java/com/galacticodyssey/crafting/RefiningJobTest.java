package com.galacticodyssey.crafting;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningJobTest {

    @Test
    void newJob_startsQueued_zeroProgress() {
        RefiningJob job = createTestJob();
        assertEquals(RefiningJobState.QUEUED, job.getState());
        assertEquals(0f, job.getProgress(), 0.001f);
    }

    @Test
    void advanceProgress_incrementsCorrectly() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(0.25f);
        assertEquals(0.25f, job.getProgress(), 0.001f);
        job.advanceProgress(0.25f);
        assertEquals(0.50f, job.getProgress(), 0.001f);
    }

    @Test
    void advanceProgress_clampedAtOne() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(1.5f);
        assertEquals(1.0f, job.getProgress(), 0.001f);
    }

    @Test
    void isComplete_trueAtFullProgress() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(1.0f);
        assertTrue(job.isComplete());
    }

    @Test
    void isComplete_falseWhenPartial() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(0.99f);
        assertFalse(job.isComplete());
    }

    @Test
    void calculateReturnedInputs_returnsProportionalToRemaining() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(0.6f);
        Map<String, Integer> returned = job.calculateReturnedInputs();
        assertEquals(2, returned.get("iron_ore"));
    }

    @Test
    void calculateReturnedInputs_queuedJob_returnsAll() {
        RefiningJob job = createTestJob();
        Map<String, Integer> returned = job.calculateReturnedInputs();
        assertEquals(5, returned.get("iron_ore"));
    }

    @Test
    void calculateReturnedInputs_completeJob_returnsNothing() {
        RefiningJob job = createTestJob();
        job.setState(RefiningJobState.ACTIVE);
        job.advanceProgress(1.0f);
        Map<String, Integer> returned = job.calculateReturnedInputs();
        assertEquals(0, returned.get("iron_ore"));
    }

    @Test
    void outputs_matchConstructorValues() {
        RefiningJob job = createTestJob();
        assertEquals(1, job.getOutputs().size());
        assertEquals("iron_concentrate", job.getOutputs().get(0).materialId);
        assertEquals(3, job.getOutputs().get(0).quantity);
    }

    private RefiningJob createTestJob() {
        return new RefiningJob(
            "process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            30.0f
        );
    }
}
