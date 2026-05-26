package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhittakerGridTest {

    @Test
    void coldWetIsIceSheet() {
        assertEquals(BiomeType.ICE_SHEET, WhittakerGrid.classify(240f, 0.9f));
    }

    @Test
    void hotDryIsVolcanic() {
        assertEquals(BiomeType.VOLCANIC, WhittakerGrid.classify(320f, 0.1f));
    }

    @Test
    void warmMoistIsGrassland() {
        assertEquals(BiomeType.GRASSLAND, WhittakerGrid.classify(290f, 0.6f));
    }

    @Test
    void coolMidIsTempForest() {
        assertEquals(BiomeType.TEMPERATE_FOREST, WhittakerGrid.classify(265f, 0.6f));
    }

    @Test
    void allGridCellsReachable() {
        float[] temps = { 240f, 260f, 290f, 320f };
        float[] moists = { 0.1f, 0.35f, 0.6f, 0.85f };
        java.util.Set<BiomeType> seen = new java.util.HashSet<>();
        for (float t : temps) {
            for (float m : moists) {
                seen.add(WhittakerGrid.classify(t, m));
            }
        }
        assertEquals(16, seen.size(), "All 16 grid biomes should be reachable");
    }

    @Test
    void noNullReturns() {
        for (float t = 150f; t < 500f; t += 10f) {
            for (float m = 0f; m <= 1.0f; m += 0.1f) {
                assertNotNull(WhittakerGrid.classify(t, m),
                    "Null at temp=" + t + " moisture=" + m);
            }
        }
    }
}
