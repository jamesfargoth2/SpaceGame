package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RngUtilTest {

    @Test
    void floatRangeReturnsValueInBounds() {
        Random rng = new Random(42L);
        for (int i = 0; i < 1000; i++) {
            float v = RngUtil.range(rng, 5f, 10f);
            assertTrue(v >= 5f && v < 10f, "Value " + v + " out of range [5, 10)");
        }
    }

    @Test
    void floatRangeIsDeterministic() {
        Random rng1 = new Random(42L);
        Random rng2 = new Random(42L);
        for (int i = 0; i < 100; i++) {
            assertEquals(RngUtil.range(rng1, 0f, 1f), RngUtil.range(rng2, 0f, 1f));
        }
    }

    @Test
    void intRangeReturnsValueInBounds() {
        Random rng = new Random(42L);
        for (int i = 0; i < 1000; i++) {
            int v = RngUtil.range(rng, 3, 7);
            assertTrue(v >= 3 && v < 7, "Value " + v + " out of range [3, 7)");
        }
    }
}
