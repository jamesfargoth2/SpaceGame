package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CraterSpecTest {

    @Test
    void simpleCraterBelowThreshold() {
        CraterSpec spec = CraterSpec.fromDiameter(10f, 2.0f, new Random(1L));
        assertEquals(CraterMorphology.SIMPLE, spec.morphology);
    }

    @Test
    void complexCraterAboveThreshold() {
        CraterSpec spec = CraterSpec.fromDiameter(50f, 1.0f, new Random(2L));
        assertEquals(CraterMorphology.COMPLEX, spec.morphology);
    }

    @Test
    void multiRingForLargeDiameter() {
        CraterSpec spec = CraterSpec.fromDiameter(300f, 0.5f, new Random(3L));
        assertEquals(CraterMorphology.MULTI_RING, spec.morphology);

        CraterSpec spec2 = CraterSpec.fromDiameter(500f, 1.0f, new Random(4L));
        assertEquals(CraterMorphology.MULTI_RING, spec2.morphology);
    }

    @Test
    void depthScalesWithDiameter() {
        Random rng1 = new Random(10L);
        Random rng2 = new Random(10L);
        CraterSpec small = CraterSpec.fromDiameter(5f, 1.0f, rng1);
        CraterSpec large = CraterSpec.fromDiameter(12f, 1.0f, rng2);
        assertTrue(large.depthKm > small.depthKm,
                "Larger crater should be deeper: " + large.depthKm + " vs " + small.depthKm);
    }

    @Test
    void freshCratersHaveRays() {
        int rayCount = 0;
        int trials = 200;
        for (int i = 0; i < trials; i++) {
            CraterSpec spec = CraterSpec.fromDiameter(20f, 0f, new Random(i));
            if (spec.hasRaySystem) rayCount++;
        }
        // With age=0 and p=0.6, expect ~60% to have rays.
        // Allow broad margin for randomness.
        assertTrue(rayCount > trials * 0.3,
                "Expected many fresh craters with rays, got " + rayCount + "/" + trials);
    }

    @Test
    void oldCratersNoRays() {
        int rayCount = 0;
        int trials = 200;
        for (int i = 0; i < trials; i++) {
            CraterSpec spec = CraterSpec.fromDiameter(20f, 5.0f, new Random(i));
            if (spec.hasRaySystem) rayCount++;
        }
        // With age > 1, hasRaySystem should always be false
        assertEquals(0, rayCount,
                "Old craters (age > 1 Gyr) should never have ray systems");
    }

    @Test
    void complexCraterHasCentralPeak() {
        CraterSpec spec = CraterSpec.fromDiameter(100f, 0.5f, new Random(42L));
        assertEquals(CraterMorphology.COMPLEX, spec.morphology);
        assertTrue(spec.centralPeakHeightKm > 0f,
                "COMPLEX crater should have a central peak");
    }

    @Test
    void simpleCraterHasNoCentralPeak() {
        CraterSpec spec = CraterSpec.fromDiameter(5f, 0.5f, new Random(42L));
        assertEquals(CraterMorphology.SIMPLE, spec.morphology);
        assertEquals(0f, spec.centralPeakHeightKm,
                "SIMPLE crater should have no central peak");
    }

    @Test
    void ejectaRadiusLargerThanCrater() {
        CraterSpec spec = CraterSpec.fromDiameter(30f, 1.0f, new Random(99L));
        assertTrue(spec.ejectaRadiusKm > spec.diameterKm * 0.5f,
                "Ejecta radius should extend beyond the rim");
    }
}
