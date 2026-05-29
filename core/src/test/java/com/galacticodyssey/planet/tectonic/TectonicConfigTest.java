package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TectonicConfigTest {

    @Test
    void defaultsAreSane() {
        TectonicConfig c = TectonicConfig.defaults();
        assertTrue(c.plateCountMin >= 3 && c.plateCountMin <= c.plateCountMax);
        assertTrue(c.boundaryInfluence > 0f);
        // Ocean worlds should target less continental crust than Terran worlds.
        assertTrue(c.continentalFractionTarget(PlanetType.OCEAN)
                 < c.continentalFractionTarget(PlanetType.TERRAN));
    }

    @Test
    void fromJsonOverridesDefaults() {
        String jsonText = "{ plateCountMin: 9, plateCountMax: 11, mountainUplift: 0.9 }";
        JsonValue root = new JsonReader().parse(jsonText);
        TectonicConfig c = TectonicConfig.fromJson(root);
        assertEquals(9, c.plateCountMin);
        assertEquals(11, c.plateCountMax);
        assertEquals(0.9f, c.mountainUplift, 1e-6f);
        // Unspecified fields keep their defaults.
        assertEquals(TectonicConfig.defaults().boundaryInfluence, c.boundaryInfluence, 1e-6f);
    }
}
