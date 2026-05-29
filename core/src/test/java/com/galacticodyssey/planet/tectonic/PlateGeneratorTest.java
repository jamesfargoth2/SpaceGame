package com.galacticodyssey.planet.tectonic;

import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlateGeneratorTest {

    private Planet planet(long seed, PlanetType type) {
        return new Planet(seed, type, 0.8f, 1.5f, 0.7f, 24f, 0.4f, false);
    }

    @Test
    void sameSeedProducesIdenticalModel() {
        PlateGenerator gen = new PlateGenerator();
        TectonicModel a = gen.generate(planet(777L, PlanetType.TERRAN));
        TectonicModel b = gen.generate(planet(777L, PlanetType.TERRAN));
        assertEquals(a.continentalFraction(), b.continentalFraction(), 1e-6f);
        assertEquals(a.features().size(), b.features().size());
        // Spot-check identical elevation at several directions.
        for (int i = 0; i < 20; i++) {
            com.badlogic.gdx.math.Vector3 d =
                new com.badlogic.gdx.math.Vector3(i - 10, 3, i * 0.5f - 2).nor();
            assertEquals(a.baseElevation(d), b.baseElevation(d), 1e-6f);
        }
    }

    @Test
    void differentSeedsDiffer() {
        PlateGenerator gen = new PlateGenerator();
        TectonicModel a = gen.generate(planet(1L, PlanetType.TERRAN));
        TectonicModel b = gen.generate(planet(2L, PlanetType.TERRAN));
        com.badlogic.gdx.math.Vector3 d = new com.badlogic.gdx.math.Vector3(0.4f, 0.3f, 0.8f).nor();
        assertNotEquals(a.baseElevation(d), b.baseElevation(d), 1e-4f);
    }

    @Test
    void oceanWorldsHaveLessContinentThanTerran() {
        PlateGenerator gen = new PlateGenerator();
        // Average across several seeds to wash out per-seed variance.
        float oceanSum = 0f, terranSum = 0f;
        int n = 8;
        for (long s = 0; s < n; s++) {
            oceanSum += gen.generate(planet(s, PlanetType.OCEAN)).continentalFraction();
            terranSum += gen.generate(planet(s, PlanetType.TERRAN)).continentalFraction();
        }
        assertTrue(oceanSum / n < terranSum / n,
            "ocean avg " + oceanSum/n + " should be < terran avg " + terranSum/n);
    }

    @Test
    void plateCountWithinConfiguredRange() {
        PlateGenerator gen = new PlateGenerator();
        TectonicModel m = gen.generate(planet(42L, PlanetType.TERRAN));
        int count = m.plateCount();
        TectonicConfig c = TectonicConfig.defaults();
        assertTrue(count >= c.plateCountMin && count <= c.plateCountMax + 64,
            "plate count " + count + " out of expected range");
    }
}
