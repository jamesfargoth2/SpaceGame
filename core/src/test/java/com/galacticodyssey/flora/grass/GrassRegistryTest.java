package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrassRegistryTest {
    private static final String JSON = "{ \"cellSize\": 32.0, \"radius\": 140.0, \"fadeBand\": 24.0," +
        "  \"baseTuftsPerM2\": 0.25, \"bladesPerTuft\": 3, \"maxCachedCells\": 256," +
        "  \"wind\": { \"amplitude\": 0.18, \"frequency\": 1.3 }," +
        "  \"biomes\": [" +
        "    { \"biome\": \"GRASSLAND\", \"density\": 1.0, \"height\": [0.5,1.1], \"colorA\": \"3a6b22\", \"colorB\": \"5a8a2e\" }," +
        "    { \"biome\": \"TUNDRA\", \"density\": 0.3, \"height\": [0.15,0.4], \"colorA\": \"4a5a3a\", \"colorB\": \"5a6545\" }" +
        "  ] }";

    @Test
    void loadsGlobalsAndBiomes() {
        GrassRegistry reg = new GrassRegistry();
        reg.loadFromJson(JSON);
        GrassConfig c = reg.config();
        assertEquals(32.0f, c.cellSize);
        assertEquals(140.0f, c.radius);
        assertEquals(0.25f, c.baseTuftsPerM2);
        assertEquals(3, c.bladesPerTuft);
        assertEquals(256, c.maxCachedCells);
        assertEquals(0.18f, c.windAmplitude);
        assertEquals(1.3f, c.windFrequency);

        GrassConfig.BiomeGrass g = c.forBiome(BiomeType.GRASSLAND);
        assertNotNull(g);
        assertEquals(1.0f, g.density);
        assertEquals(0.5f, g.heightMin);
        assertEquals(1.1f, g.heightMax);
        assertEquals(0x3a / 255f, g.colorAr, 0.01f);

        assertNull(c.forBiome(BiomeType.DESERT)); // not listed -> no grass
    }
}
