package com.galacticodyssey.planet;

import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BiomeMapperTectonicTest {

    private Planet planet(long seed, PlanetType type) {
        return new Planet(seed, type, 0.8f, 1.5f, 0.7f, 24f, 0.4f, false);
    }

    private Atmosphere atmo() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.78f);
        comp.put(Gas.O2, 0.22f);
        return new Atmosphere(comp, 1.0f, 1.0f, 255f, 288f, true, EnumSet.noneOf(AtmoHazard.class));
    }

    @Test
    void seaLevelDerivedFromContinentalFraction() {
        BiomeMapper mapper = new BiomeMapper();
        PlateGenerator gen = new PlateGenerator();
        Planet p = planet(123L, PlanetType.TERRAN);
        TectonicModel model = gen.generate(p);

        BiomeMap map = mapper.generate(p, atmo(), (lon, lat) -> 0f, model);
        // Expected mapping: seaLevel = clamp(0.3 * (1 - continentalFraction), 0, 0.3)
        float expected = Math.max(0f, Math.min(0.3f, 0.3f * (1f - model.continentalFraction())));
        assertEquals(expected, map.seaLevel, 1e-5f);
    }

    @Test
    void moreContinentMeansLowerSeaLevel() {
        BiomeMapper mapper = new BiomeMapper();
        PlateGenerator gen = new PlateGenerator();
        Planet ocean = planet(5L, PlanetType.OCEAN);
        Planet terran = planet(5L, PlanetType.TERRAN);

        float oceanSea = mapper.generate(ocean, atmo(), (lon, lat) -> 0f, gen.generate(ocean)).seaLevel;
        float terranSea = mapper.generate(terran, atmo(), (lon, lat) -> 0f, gen.generate(terran)).seaLevel;
        assertTrue(oceanSea > terranSea,
            "ocean world sea level " + oceanSea + " should exceed terran " + terranSea);
    }
}
