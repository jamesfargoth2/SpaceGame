package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrossSectionTest {

    @Test
    void generatesCorrectNumberOfVertices() {
        CrossSection cs = new CrossSection(2f, 1.5f, 2.0f);
        float[][] ring = cs.generateRing(16);
        assertEquals(16, ring.length);
    }

    @Test
    void verticesLieWithinBounds() {
        float halfWidth = 3f;
        float halfHeight = 2f;
        CrossSection cs = new CrossSection(halfWidth, halfHeight, 2.0f);
        float[][] ring = cs.generateRing(32);

        for (float[] vertex : ring) {
            assertTrue(Math.abs(vertex[0]) <= halfWidth + 0.01f,
                "X=" + vertex[0] + " exceeds halfWidth=" + halfWidth);
            assertTrue(Math.abs(vertex[1]) <= halfHeight + 0.01f,
                "Y=" + vertex[1] + " exceeds halfHeight=" + halfHeight);
        }
    }

    @Test
    void ringIsSymmetricAcrossYAxis() {
        CrossSection cs = new CrossSection(2f, 1.5f, 2.5f);
        float[][] ring = cs.generateRing(32);

        assertEquals(2f, ring[0][0], 0.01f);
        assertEquals(0f, ring[0][1], 0.01f);

        int quarter = 32 / 4;
        assertEquals(0f, ring[quarter][0], 0.1f);
        assertTrue(ring[quarter][1] > 0);
    }

    @Test
    void exponentAffectsShape() {
        CrossSection round = new CrossSection(2f, 2f, 2.0f);
        CrossSection boxy = new CrossSection(2f, 2f, 4.0f);

        float[][] roundRing = round.generateRing(32);
        float[][] boxyRing = boxy.generateRing(32);

        int idx45 = 32 / 8;
        float roundDist = (float) Math.sqrt(roundRing[idx45][0] * roundRing[idx45][0] +
            roundRing[idx45][1] * roundRing[idx45][1]);
        float boxyDist = (float) Math.sqrt(boxyRing[idx45][0] * boxyRing[idx45][0] +
            boxyRing[idx45][1] * boxyRing[idx45][1]);

        assertTrue(boxyDist > roundDist,
            "Boxy shape should extend further at 45 degrees: boxy=" + boxyDist + " round=" + roundDist);
    }
}
