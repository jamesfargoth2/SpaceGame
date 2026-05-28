package com.galacticodyssey.flora.data;

import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloraRegistryTest {
    private static final String SPECIES = "{ \"species\": [\n" +
        "  { \"id\": \"jungle_tree\", \"displayName\": \"Canopy Tree\",\n" +
        "    \"shape\": \"ELLIPSOID\", \"height\": [8,14], \"radius\": [3,5],\n" +
        "    \"growth\": { \"attractionPoints\": 220, \"influenceRadius\": 4.0,\n" +
        "      \"killDistance\": 0.7, \"segmentLength\": 0.45, \"maxNodes\": 600 },\n" +
        "    \"trunk\": { \"sides\": 6, \"baseRadius\": 0.35, \"taper\": 0.78, \"color\": \"5a3b22\" },\n" +
        "    \"foliage\": { \"style\": \"CLUMP\", \"clumpsPerTip\": 2,\n" +
        "      \"clumpRadius\": [1.0,1.8], \"colorA\": \"2f6b2a\", \"colorB\": \"3f8a34\" },\n" +
        "    \"prototypeVariants\": 8 },\n" +
        "  { \"id\": \"desert_cactus\", \"shape\": \"COLUMN\", \"height\": [2,4], \"radius\": [0.5,0.8],\n" +
        "    \"foliage\": { \"style\": \"NONE\" } }\n" +
        "] }";

    @Test
    void loadsSpeciesWithNestedFields() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadSpecies(SPECIES);

        FloraSpecies t = reg.species("jungle_tree");
        assertNotNull(t);
        assertEquals("Canopy Tree", t.displayName);
        assertEquals(EnvelopeShape.ELLIPSOID, t.shape);
        assertEquals(8f, t.heightMin);
        assertEquals(14f, t.heightMax);
        assertEquals(220, t.attractionPoints);
        assertEquals(0.45f, t.segmentLength);
        assertEquals(6, t.trunkSides);
        assertEquals(FoliageStyle.CLUMP, t.foliageStyle);
        assertEquals(2, t.clumpsPerTip);
        assertEquals(8, t.prototypeVariants);
        assertEquals(0x5a / 255f, t.trunkColor.r, 0.01f);

        FloraSpecies c = reg.species("desert_cactus");
        assertEquals(FoliageStyle.NONE, c.foliageStyle);
        assertEquals(EnvelopeShape.COLUMN, c.shape);
    }

    @Test
    void unknownSpeciesReturnsNull() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadSpecies(SPECIES);
        assertNull(reg.species("nope"));
    }
}
