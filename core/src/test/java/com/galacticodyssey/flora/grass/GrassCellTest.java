package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrassCellTest {
    static final int STRIDE = 10;

    /** Flat terrain at y=5; biome is uniform (constructor-selected). */
    static class UniformSampler implements TerrainSampler {
        final BiomeType biome;
        UniformSampler(BiomeType b) { this.biome = b; }
        public float heightAt(float x, float z) { return 5f; }
        public BiomeType biomeAt(float x, float z) { return biome; }
    }

    static GrassConfig config() {
        GrassConfig c = new GrassConfig();
        c.cellSize = 32f; c.baseTuftsPerM2 = 0.25f;
        GrassConfig.BiomeGrass g = new GrassConfig.BiomeGrass();
        g.density = 1.0f; g.heightMin = 0.5f; g.heightMax = 1.0f;
        g.colorAr = 0.2f; g.colorAg = 0.4f; g.colorAb = 0.1f;
        g.colorBr = 0.3f; g.colorBg = 0.5f; g.colorBb = 0.2f;
        c.put(BiomeType.GRASSLAND, g);
        GrassConfig.BiomeGrass t = new GrassConfig.BiomeGrass();
        t.density = 0.3f; t.heightMin = 0.1f; t.heightMax = 0.3f;
        c.put(BiomeType.TUNDRA, t);
        return c;
    }

    @Test
    void deterministicForSameCellAndSeed() {
        GrassConfig c = config();
        float[] a = GrassCell.generate(0, 0, c, new UniformSampler(BiomeType.GRASSLAND), 999L);
        float[] b = GrassCell.generate(0, 0, c, new UniformSampler(BiomeType.GRASSLAND), 999L);
        assertArrayEquals(a, b);
        assertTrue(a.length % STRIDE == 0);
        assertTrue(a.length > 0);
    }

    @Test
    void densityScalesCountGrasslandFullTundraPartialDesertEmpty() {
        GrassConfig c = config();
        int grassland = GrassCell.generate(1, 1, c, new UniformSampler(BiomeType.GRASSLAND), 7L).length / STRIDE;
        int tundra = GrassCell.generate(1, 1, c, new UniformSampler(BiomeType.TUNDRA), 7L).length / STRIDE;
        int desert = GrassCell.generate(1, 1, c, new UniformSampler(BiomeType.DESERT), 7L).length / STRIDE;
        assertEquals(0, desert, "unlisted biome -> no grass");
        assertTrue(grassland > tundra, "density 1.0 keeps more than 0.3");
        assertTrue(tundra > 0, "density 0.3 keeps some");
        // density 1.0 keeps every candidate: count == round(cellArea * baseTuftsPerM2)
        assertEquals(Math.round(32f * 32f * 0.25f), grassland);
    }

    @Test
    void offsetsWithinCellAndSnappedToHeight() {
        GrassConfig c = config();
        int cx = 2, cz = -1;
        float[] data = GrassCell.generate(cx, cz, c, new UniformSampler(BiomeType.GRASSLAND), 5L);
        float originX = cx * c.cellSize, originZ = cz * c.cellSize;
        for (int i = 0; i < data.length; i += STRIDE) {
            float ox = data[i], oy = data[i + 1], oz = data[i + 2];
            assertTrue(ox >= originX && ox <= originX + c.cellSize, "x in cell");
            assertTrue(oz >= originZ && oz <= originZ + c.cellSize, "z in cell");
            assertEquals(5f, oy, 1e-4f, "snapped to terrain height");
            float scaleY = data[i + 4];
            assertTrue(scaleY >= 0.5f && scaleY <= 1.0f, "height in biome range");
        }
    }
}
