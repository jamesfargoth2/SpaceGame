package com.galacticodyssey.networking.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PredictionComponentTest {

    @Test
    void defaultsHaveZeroOffset() {
        PredictionComponent pc = new PredictionComponent();
        assertEquals(0f, pc.smoothingOffsetX);
        assertEquals(0f, pc.smoothingOffsetY);
        assertEquals(0f, pc.smoothingOffsetZ);
        assertEquals(0, pc.smoothingFramesRemaining);
    }

    @Test
    void sequenceStartsAtZero() {
        PredictionComponent pc = new PredictionComponent();
        assertEquals(0, pc.nextSequenceNumber);
    }

    @Test
    void advanceSequenceIncrements() {
        PredictionComponent pc = new PredictionComponent();
        int first = pc.advanceSequence();
        int second = pc.advanceSequence();
        assertEquals(0, first);
        assertEquals(1, second);
    }

    @Test
    void inputBufferIsNotNull() {
        PredictionComponent pc = new PredictionComponent();
        assertNotNull(pc.getInputBuffer());
        assertEquals(0, pc.getInputBuffer().size());
    }
}
