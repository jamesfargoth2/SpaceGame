package com.galacticodyssey.data;

import com.badlogic.gdx.graphics.Color;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WorldPopulatorColorTest {

    private static int[] perm;

    @BeforeAll
    static void setup() {
        perm = WorldPopulator.createPermutation(new Random(42L));
    }

    @Test
    void noiseVariationChangesBaseColor() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color c1 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color c2 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            50f, 50f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);

        assertNotEquals(c1.r, c2.r, 0.001f,
            "Noise variation should produce different red values at different world positions");
    }

    @Test
    void noiseVariationIsDeterministic() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color c1 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color c2 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);

        assertEquals(c1.r, c2.r, 0.0001f, "Same inputs should produce identical colors");
        assertEquals(c1.g, c2.g, 0.0001f);
        assertEquals(c1.b, c2.b, 0.0001f);
    }

    @Test
    void steepSlopeBlendTowardRock() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color flat = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color steep = WorldPopulator.biomeColor(biome, 0.5f, 0.7f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);

        float rockR = 0.42f, rockG = 0.38f, rockB = 0.32f;
        float flatDistToRock = Math.abs(flat.r - rockR) + Math.abs(flat.g - rockG) + Math.abs(flat.b - rockB);
        float steepDistToRock = Math.abs(steep.r - rockR) + Math.abs(steep.g - rockG) + Math.abs(steep.b - rockB);

        assertTrue(steepDistToRock < flatDistToRock,
            "Steep slopes should be closer to rock color than flat ground");
    }

    @Test
    void highAltitudeBlendTowardSnow() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color low = WorldPopulator.biomeColor(biome, 0.3f, 0f,
            10f, 10f, 24f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color high = WorldPopulator.biomeColor(biome, 0.3f, 0f,
            10f, 10f, 72f, 0f, 80f, perm, grid, size, size, 2, 2);

        float snowR = 0.92f, snowG = 0.93f, snowB = 0.95f;
        float lowDistToSnow = Math.abs(low.r - snowR) + Math.abs(low.g - snowG) + Math.abs(low.b - snowB);
        float highDistToSnow = Math.abs(high.r - snowR) + Math.abs(high.g - snowG) + Math.abs(high.b - snowB);

        assertTrue(highDistToSnow < lowDistToSnow,
            "High altitude vertices should be closer to snow color");
    }

    @Test
    void biomeEdgeBlendsDifferentFromInterior() {
        int size = 5;
        BiomeType[] grid = new BiomeType[size * size];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = BiomeType.GRASSLAND;
        }
        grid[1 * size + 2] = BiomeType.DESERT;

        Color edgeColor = WorldPopulator.biomeColor(BiomeType.GRASSLAND, 0.5f, 0f,
            10f, 10f, 40f, 0f, 80f, perm, grid, size, size, 2, 1);

        BiomeType[] uniformGrid = uniformGrid(BiomeType.GRASSLAND, size);
        Color interiorColor = WorldPopulator.biomeColor(BiomeType.GRASSLAND, 0.5f, 0f,
            10f, 10f, 40f, 0f, 80f, perm, uniformGrid, size, size, 2, 1);

        float diff = Math.abs(edgeColor.r - interiorColor.r)
            + Math.abs(edgeColor.g - interiorColor.g)
            + Math.abs(edgeColor.b - interiorColor.b);
        assertTrue(diff > 0.01f,
            "Vertices near a biome boundary should differ from interior vertices");
    }

    private static BiomeType[] uniformGrid(BiomeType biome, int size) {
        BiomeType[] grid = new BiomeType[size * size];
        for (int i = 0; i < grid.length; i++) grid[i] = biome;
        return grid;
    }
}
