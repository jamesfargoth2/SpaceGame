package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CraterCarverTest {

    @Test
    void craterCreatesDepression() {
        int size = 64;
        float[] heights = new float[size * size];
        // Start with flat terrain at height 10
        java.util.Arrays.fill(heights, 10f);

        CraterSpec spec = CraterSpec.fromDiameter(10f, 0f, new Random(1L));
        int cx = size / 2;
        int cz = size / 2;
        float rimR = 8f;

        CraterCarver.carve(heights, size, size, cx, cz, rimR, spec, 1.0f);

        float centerHeight = heights[cz * size + cx];
        assertTrue(centerHeight < 10f,
                "Center should be depressed: " + centerHeight + " should be < 10");
    }

    @Test
    void rimIsRaised() {
        int size = 64;
        float[] heights = new float[size * size];
        java.util.Arrays.fill(heights, 0f);

        CraterSpec spec = CraterSpec.fromDiameter(10f, 0f, new Random(1L));
        int cx = size / 2;
        int cz = size / 2;
        float rimR = 10f;

        CraterCarver.carve(heights, size, size, cx, cz, rimR, spec, 1.0f);

        // Check height just beyond the rim
        int rimX = cx + (int) rimR + 1;
        if (rimX < size) {
            float rimHeight = heights[cz * size + rimX];
            assertTrue(rimHeight > 0f,
                    "Height just beyond rim should be raised: " + rimHeight);
        }
    }

    @Test
    void centralPeakOnlyForComplex() {
        int size = 128;

        // Test SIMPLE crater — no central peak
        float[] simpleHeights = new float[size * size];
        java.util.Arrays.fill(simpleHeights, 100f);
        CraterSpec simple = CraterSpec.fromDiameter(10f, 0f, new Random(1L));
        assertEquals(CraterMorphology.SIMPLE, simple.morphology);
        CraterCarver.carve(simpleHeights, size, size, size / 2, size / 2, 20f, simple, 1.0f);
        float simpleCenterH = simpleHeights[(size / 2) * size + (size / 2)];

        // For SIMPLE: center should be the deepest point (no peak)
        // Check that neighbors around center are not deeper
        float simpleNearCenter = simpleHeights[(size / 2) * size + (size / 2 + 2)];
        assertTrue(simpleCenterH <= simpleNearCenter,
                "SIMPLE crater center should be deepest point");

        // Test COMPLEX crater — has central peak
        float[] complexHeights = new float[size * size];
        java.util.Arrays.fill(complexHeights, 100f);
        CraterSpec complex = CraterSpec.fromDiameter(50f, 0f, new Random(1L));
        assertEquals(CraterMorphology.COMPLEX, complex.morphology);
        CraterCarver.carve(complexHeights, size, size, size / 2, size / 2, 20f, complex, 1.0f);
        float complexCenterH = complexHeights[(size / 2) * size + (size / 2)];

        // For COMPLEX: center should be higher than the flat floor area
        // Check a point in the flat floor zone (around 40-50% of rimR from center)
        int floorOffset = (int) (20f * 0.45f); // ~45% of rimR
        float complexFloorH = complexHeights[(size / 2) * size + (size / 2 + floorOffset)];
        assertTrue(complexCenterH > complexFloorH,
                "COMPLEX crater center (" + complexCenterH +
                ") should be higher than floor (" + complexFloorH + ") due to central peak");
    }

    @Test
    void carveDoesNotAffectDistantPixels() {
        int size = 128;
        float[] heights = new float[size * size];
        java.util.Arrays.fill(heights, 5f);

        CraterSpec spec = CraterSpec.fromDiameter(5f, 0f, new Random(1L));
        CraterCarver.carve(heights, size, size, 32, 32, 5f, spec, 1.0f);

        // Far corner should be unaffected
        assertEquals(5f, heights[127 * size + 127], 1e-6f,
                "Distant vertex should be unaffected");
    }

    @Test
    void ageDegradesCrater() {
        int size = 64;
        float[] freshHeights = new float[size * size];
        float[] oldHeights = new float[size * size];
        java.util.Arrays.fill(freshHeights, 10f);
        java.util.Arrays.fill(oldHeights, 10f);

        Random r1 = new Random(42L);
        Random r2 = new Random(42L);
        CraterSpec fresh = CraterSpec.fromDiameter(10f, 0f, r1);
        CraterSpec old = CraterSpec.fromDiameter(10f, 4.0f, r2);

        int cx = size / 2, cz = size / 2;
        float rimR = 10f;

        CraterCarver.carve(freshHeights, size, size, cx, cz, rimR, fresh, 1.0f);
        CraterCarver.carve(oldHeights, size, size, cx, cz, rimR, old, 1.0f);

        float freshCenter = freshHeights[cz * size + cx];
        float oldCenter = oldHeights[cz * size + cx];

        // Old crater should be shallower (less depression) than fresh crater
        assertTrue(oldCenter > freshCenter,
                "Old crater should be shallower: old=" + oldCenter + " fresh=" + freshCenter);
    }
}
