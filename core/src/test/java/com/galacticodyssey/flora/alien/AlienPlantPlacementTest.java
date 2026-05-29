package com.galacticodyssey.flora.alien;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AlienPlantPlacementTest {
    private static final String JSON = "{ \"species\": [ { \"id\": \"glowcap\", \"archetype\": \"BIOLUMINESCENT\", \"prototypeVariants\": 4 } ]," +
        " \"palette\": [ { \"biome\": \"SWAMP\", \"density\": 1.0, \"species\": [ {\"id\":\"glowcap\",\"weight\":1} ] }," +
        "               { \"biome\": \"DESERT\", \"density\": 0.0, \"species\": [] } ] }";

    private static AlienPlantRegistry reg() {
        AlienPlantRegistry r = new AlienPlantRegistry();
        r.loadSpecies(JSON); r.loadPalette(JSON); return r;
    }
    private static float[] flat(int v){ float[] h=new float[v*v]; java.util.Arrays.fill(h,1f); return h; }
    private static BiomeType[] uniform(int v, BiomeType b){ BiomeType[] g=new BiomeType[v*v]; java.util.Arrays.fill(g,b); return g; }

    @Test
    void placesInPaletteBiomeNotElsewhere() {
        int v=33; float[] hm=flat(v); AlienPlantRegistry r=reg();
        List<AlienPlantPlacement> swamp = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.SWAMP), hm, v,v,100f,100f,0f,99L,300);
        List<AlienPlantPlacement> desert = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.DESERT), hm, v,v,100f,100f,0f,99L,300);
        assertFalse(swamp.isEmpty());
        assertTrue(desert.isEmpty());
        for (AlienPlantPlacement p : swamp) {
            assertEquals("glowcap", p.speciesId);
            assertTrue(p.variantIndex >= 0 && p.variantIndex < 4);
            assertTrue(p.scale > 0f);
        }
    }

    @Test
    void isDeterministic() {
        int v=33; float[] hm=flat(v); AlienPlantRegistry r=reg();
        List<AlienPlantPlacement> a = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.SWAMP), hm, v,v,100f,100f,0f,5L,300);
        List<AlienPlantPlacement> b = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.SWAMP), hm, v,v,100f,100f,0f,5L,300);
        assertEquals(a.size(), b.size());
        for (int i=0;i<a.size();i++){ assertEquals(a.get(i).x,b.get(i).x); assertEquals(a.get(i).variantIndex,b.get(i).variantIndex); }
    }
}
