package com.galacticodyssey.flora.data;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BiomePaletteTest {
    private static final String PALETTES = "{ \"palettes\": [\n" +
        "  { \"biome\": \"TROPICAL_FOREST\", \"density\": 0.85, \"tintJitter\": 0.08,\n" +
        "    \"species\": [ { \"id\": \"jungle_tree\", \"weight\": 0.75 },\n" +
        "                  { \"id\": \"understory\", \"weight\": 0.25 } ] },\n" +
        "  { \"biome\": \"OCEAN\", \"density\": 0.0, \"species\": [] }\n" +
        "] }";

    @Test
    void loadsPaletteByBiome() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadPalettes(PALETTES);
        BiomePalette p = reg.palette(BiomeType.TROPICAL_FOREST);
        assertNotNull(p);
        assertEquals(0.85f, p.density);
        assertEquals(2, p.entries.size());
        assertTrue(reg.palette(BiomeType.OCEAN).isEmpty());
        assertNull(reg.palette(BiomeType.DESERT)); // not defined
    }

    @Test
    void weightedPickIsDeterministicAndRespectsWeights() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadPalettes(PALETTES);
        BiomePalette p = reg.palette(BiomeType.TROPICAL_FOREST);

        assertEquals(p.pickSpecies(new Random(42)), p.pickSpecies(new Random(42)));

        Random rng = new Random(7);
        int jungle = 0;
        for (int i = 0; i < 2000; i++) if ("jungle_tree".equals(p.pickSpecies(rng))) jungle++;
        assertTrue(jungle > 1300 && jungle < 1700, "expected ~75% jungle_tree, got " + jungle);
    }

    @Test
    void emptyPalettePicksNull() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadPalettes(PALETTES);
        assertNull(reg.palette(BiomeType.OCEAN).pickSpecies(new Random(1)));
    }
}
