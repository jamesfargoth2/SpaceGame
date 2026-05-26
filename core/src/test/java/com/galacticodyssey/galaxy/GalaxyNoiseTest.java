package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyNoiseTest {

    @Test
    void fbmReturnsSameValueForSameInput() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        float a = noise.fbm(1.5f, 2.5f, 4, 0.5f, 2.0f);
        float b = noise.fbm(1.5f, 2.5f, 4, 0.5f, 2.0f);
        assertEquals(a, b, 1e-6f);
    }

    @Test
    void fbmReturnsDifferentValuesForDifferentInputs() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        float a = noise.fbm(0f, 0f, 4, 0.5f, 2.0f);
        float b = noise.fbm(100f, 100f, 4, 0.5f, 2.0f);
        assertNotEquals(a, b, 1e-6f);
    }

    @Test
    void fbmOutputInExpectedRange() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        for (int i = 0; i < 1000; i++) {
            float x = i * 0.37f;
            float y = i * 0.71f;
            float v = noise.fbm(x, y, 6, 0.5f, 2.0f);
            assertTrue(v >= -1.1f && v <= 1.1f,
                "fBm value " + v + " outside expected range at (" + x + "," + y + ")");
        }
    }

    @Test
    void differentSeedsProduceDifferentNoise() {
        GalaxyNoise noiseA = new GalaxyNoise(1L);
        GalaxyNoise noiseB = new GalaxyNoise(2L);
        float a = noiseA.fbm(5f, 5f, 4, 0.5f, 2.0f);
        float b = noiseB.fbm(5f, 5f, 4, 0.5f, 2.0f);
        assertNotEquals(a, b, 1e-6f);
    }

    @Test
    void warpedNoiseReturnsDeterministicValues() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        float a = noise.warpedNoise(1f, 1f, 0.5f);
        float b = noise.warpedNoise(1f, 1f, 0.5f);
        assertEquals(a, b, 1e-6f);
    }
}
