package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeightmapTerrainSamplerTest {
    @Test
    void heightAndBiomeMatchUnderlyingArrays() {
        int v = 3;
        float w = 100f, d = 100f;
        // 3x3 flat heightmap at y=2
        float[] hm = { 2,2,2, 2,2,2, 2,2,2 };
        BiomeType[] biomes = {
            BiomeType.OCEAN, BiomeType.OCEAN, BiomeType.OCEAN,
            BiomeType.OCEAN, BiomeType.GRASSLAND, BiomeType.OCEAN,
            BiomeType.OCEAN, BiomeType.OCEAN, BiomeType.OCEAN
        };
        HeightmapTerrainSampler s = new HeightmapTerrainSampler(hm, biomes, v, v, w, d);

        assertEquals(2f, s.heightAt(0f, 0f), 1e-4f);
        // centre cell (0,0 world) maps to grid centre -> GRASSLAND
        assertEquals(BiomeType.GRASSLAND, s.biomeAt(0f, 0f));
        // far corner clamps into range, stays OCEAN
        assertEquals(BiomeType.OCEAN, s.biomeAt(-49f, -49f));
        // way out of bounds clamps to edge (no exception)
        assertEquals(BiomeType.OCEAN, s.biomeAt(9999f, 9999f));
    }
}
