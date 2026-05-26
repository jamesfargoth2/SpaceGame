package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyDensityFieldTest {

    private GalaxyDensityField field;
    private GalaxyConfig config;
    private GalaxyNoise noise;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        noise = new GalaxyNoise(42L);
        field = new GalaxyDensityField();
    }

    @Test
    void coreDensityHigherThanRim() {
        float core = field.density(0f, 0f, config, noise);
        float rim = field.density(0.9f, 0f, config, noise);
        assertTrue(core > rim, "Core density " + core + " should exceed rim density " + rim);
    }

    @Test
    void centreDensityAboveHalf() {
        float centre = field.density(0f, 0f, config, noise);
        assertTrue(centre > 0.5f, "Centre density " + centre + " should be > 0.5");
    }

    @Test
    void densityOutsideDiskIsZero() {
        float outside = field.density(1.5f, 0f, config, noise);
        assertEquals(0f, outside, 1e-5f);
    }

    @Test
    void densityClampedBetweenZeroAndOne() {
        for (float x = -1f; x <= 1f; x += 0.1f) {
            for (float y = -1f; y <= 1f; y += 0.1f) {
                float d = field.density(x, y, config, noise);
                assertTrue(d >= 0f && d <= 1f, "Density " + d + " out of [0,1] at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void densityIsDeterministic() {
        GalaxyNoise noise2 = new GalaxyNoise(42L);
        float a = field.density(0.3f, 0.4f, config, noise);
        float b = field.density(0.3f, 0.4f, config, noise2);
        assertEquals(a, b, 1e-6f);
    }

    @Test
    void spiralArmDenserThanInterArm() {
        float onArm = field.density(0.5f, 0f, config, noise);
        float offArm = field.density(
            0.5f * (float) Math.cos(Math.PI / 4),
            0.5f * (float) Math.sin(Math.PI / 4),
            config, noise);
        assertTrue(onArm > offArm,
            "On-arm density " + onArm + " should exceed off-arm density " + offArm);
    }
}
