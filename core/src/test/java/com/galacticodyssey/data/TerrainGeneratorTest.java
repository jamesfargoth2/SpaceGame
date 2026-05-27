// core/src/test/java/com/galacticodyssey/data/TerrainGeneratorTest.java
package com.galacticodyssey.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainGeneratorTest {

    @Test
    void heightmapHasCorrectDimensions() {
        float[] heights = TerrainGenerator.generateHeightmap(257, 257, 500f, 500f, 42L);
        assertEquals(257 * 257, heights.length);
    }

    @Test
    void heightmapValuesAreInRange() {
        float[] heights = TerrainGenerator.generateHeightmap(257, 257, 500f, 500f, 42L);
        for (float h : heights) {
            assertTrue(h >= -150f && h <= 200f,
                "Height " + h + " is outside expected range [-150, 200]");
        }
    }

    @Test
    void getHeightAtReturnsInterpolatedValue() {
        float[] heights = TerrainGenerator.generateHeightmap(257, 257, 500f, 500f, 42L);
        float h = TerrainGenerator.getHeightAt(heights, 257, 257, 500f, 500f, 0f, 0f);
        assertFalse(Float.isNaN(h), "Height at center should be a valid number");
    }

    @Test
    void normalsAreUnitLength() {
        float[] heights = TerrainGenerator.generateHeightmap(5, 5, 10f, 10f, 42L);
        float[] normals = TerrainGenerator.computeNormals(heights, 5, 5, 10f, 10f);
        assertEquals(5 * 5 * 3, normals.length);

        for (int i = 0; i < normals.length; i += 3) {
            float len = (float) Math.sqrt(
                normals[i] * normals[i] +
                normals[i + 1] * normals[i + 1] +
                normals[i + 2] * normals[i + 2]);
            assertEquals(1f, len, 0.01f, "Normal at index " + i + " should be unit length");
        }
    }

    @Test
    void differentSeedsProduceDifferentTerrain() {
        float[] h1 = TerrainGenerator.generateHeightmap(33, 33, 100f, 100f, 1L);
        float[] h2 = TerrainGenerator.generateHeightmap(33, 33, 100f, 100f, 2L);

        boolean anyDifferent = false;
        for (int i = 0; i < h1.length; i++) {
            if (Math.abs(h1[i] - h2[i]) > 0.001f) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "Different seeds should produce different terrain");
    }
}
