package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BiomeMapperTest {
    private static final long GALAXY_SEED = 42L;
    private BiomeMapper biomeMapper;
    private AtmosphereGenerator atmoGen;
    private StarSystemGenerator starGen;
    private OrbitalLayoutGenerator layoutGen;
    private PlanetGenerator planetGen;

    @BeforeEach
    void setUp() {
        biomeMapper = new BiomeMapper();
        atmoGen = new AtmosphereGenerator();
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
        planetGen = new PlanetGenerator(GALAXY_SEED);
    }

    @Test
    void sameSeedProducesIdenticalBiomeMap() {
        Planet planet = generateTerranPlanet();
        if (planet == null) return;
        Atmosphere atmo = atmoGen.generate(planet, generateTestSystem(42L));
        BiomeMap a = biomeMapper.generate(planet, atmo);
        BiomeMap b = new BiomeMapper().generate(planet, atmo);
        assertEquals(a.seaLevel, b.seaLevel, 1e-6f);
        assertEquals(a.baseMoisture, b.baseMoisture, 1e-6f);
    }

    @Test
    void onlyAllowedBiomesReturnedForPlanetType() {
        for (long id = 0; id < 100; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            sys.orbits.addAll(orbits);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                slot.planet = p;
                if (!p.type.hasSurface()) continue;
                Atmosphere atmo = atmoGen.generate(p, sys);
                BiomeMap bm = biomeMapper.generate(p, atmo);
                for (float lat = -1.5f; lat <= 1.5f; lat += 0.5f) {
                    for (float lon = -3.0f; lon <= 3.0f; lon += 1.0f) {
                        BiomeType biome = bm.getBiome(lat, lon, 0.3f);
                        assertTrue(bm.allowedBiomes.contains(biome),
                            "Planet type " + p.type + " returned disallowed biome " + biome);
                    }
                }
            }
        }
    }

    @Test
    void polarRegionsColderThanEquator() {
        Planet planet = generateTerranPlanet();
        if (planet == null) return;
        Atmosphere atmo = atmoGen.generate(planet, generateTestSystem(42L));
        BiomeMap bm = biomeMapper.generate(planet, atmo);
        float equatorTemp = bm.getTemperature(0f, 0f);
        float poleTemp = bm.getTemperature((float)(Math.PI / 2.0), 0f);
        assertTrue(equatorTemp > poleTemp,
            "Equator temp " + equatorTemp + " should exceed pole temp " + poleTemp);
    }

    private Planet generateTerranPlanet() {
        for (long id = 0; id < 500; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            sys.orbits.addAll(orbits);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                slot.planet = p;
                if (p.type == PlanetType.TERRAN) return p;
            }
        }
        return null;
    }

    private StarSystem generateTestSystem(long id) {
        StarPosition pos = new StarPosition();
        pos.uniqueId = id;
        pos.x = id * 10.0;
        pos.y = id * 5.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;
        return starGen.generate(pos, GalaxyRegion.INNER_RIM);
    }
}
