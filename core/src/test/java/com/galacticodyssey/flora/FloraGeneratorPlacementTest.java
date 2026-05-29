package com.galacticodyssey.flora;

import com.galacticodyssey.flora.data.FloraRegistry;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FloraGeneratorPlacementTest {
    private static final String SPECIES = "{ \"species\": [" +
        "{ \"id\": \"tree\", \"shape\": \"ELLIPSOID\", \"prototypeVariants\": 4 } ] }";
    private static final String PALETTES = "{ \"palettes\": [" +
        "{ \"biome\": \"TROPICAL_FOREST\", \"density\": 1.0, \"species\": [ { \"id\": \"tree\", \"weight\": 1 } ] }," +
        "{ \"biome\": \"DESERT\", \"density\": 0.0, \"species\": [] } ] }";

    private static FloraRegistry registry() {
        FloraRegistry r = new FloraRegistry();
        r.loadSpecies(SPECIES);
        r.loadPalettes(PALETTES);
        return r;
    }

    private static float[] flatHeightmap(int verts) {
        float[] h = new float[verts * verts];
        java.util.Arrays.fill(h, 1.0f);
        return h;
    }

    private static BiomeType[] uniformBiome(int verts, BiomeType b) {
        BiomeType[] g = new BiomeType[verts * verts];
        java.util.Arrays.fill(g, b);
        return g;
    }

    @Test
    void placesInForestNotInDesert() {
        int v = 33;
        float[] hm = flatHeightmap(v);
        FloraRegistry reg = registry();

        List<FloraPlacement> forest = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.TROPICAL_FOREST), hm, v, v, 100f, 100f, 0f, 999L, 300);
        List<FloraPlacement> desert = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.DESERT), hm, v, v, 100f, 100f, 0f, 999L, 300);

        assertFalse(forest.isEmpty(), "density 1.0 forest should place trees");
        assertTrue(desert.isEmpty(), "density 0 desert should place nothing");
        for (FloraPlacement p : forest) {
            assertEquals("tree", p.speciesId);
            assertTrue(p.variantIndex >= 0 && p.variantIndex < 4);
            assertTrue(p.scale > 0f);
        }
    }

    @Test
    void isDeterministic() {
        int v = 33;
        float[] hm = flatHeightmap(v);
        FloraRegistry reg = registry();
        List<FloraPlacement> a = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.TROPICAL_FOREST), hm, v, v, 100f, 100f, 0f, 5L, 300);
        List<FloraPlacement> b = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.TROPICAL_FOREST), hm, v, v, 100f, 100f, 0f, 5L, 300);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x, b.get(i).x);
            assertEquals(a.get(i).z, b.get(i).z);
            assertEquals(a.get(i).variantIndex, b.get(i).variantIndex);
        }
    }
}
