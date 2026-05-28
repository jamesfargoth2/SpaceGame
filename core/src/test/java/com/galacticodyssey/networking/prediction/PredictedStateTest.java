package com.galacticodyssey.networking.prediction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PredictedStateTest {

    @Test
    void storesAllFields() {
        PredictedState s = new PredictedState(1, 2, 3, 0, 0, 0, 1, 4, 5, 6);
        assertEquals(1f, s.posX);
        assertEquals(2f, s.posY);
        assertEquals(3f, s.posZ);
        assertEquals(1f, s.rotW);
        assertEquals(4f, s.velX);
    }

    @Test
    void distanceToSamePositionIsZero() {
        PredictedState a = new PredictedState(5, 10, 15, 0, 0, 0, 1, 0, 0, 0);
        assertEquals(0f, a.distanceTo(a), 1e-6f);
    }

    @Test
    void distanceToComputesEuclidean() {
        PredictedState a = new PredictedState(0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        PredictedState b = new PredictedState(3, 4, 0, 0, 0, 0, 1, 0, 0, 0);
        assertEquals(5f, a.distanceTo(b), 1e-6f);
    }
}
