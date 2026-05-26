package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarSystemGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private StarSystemGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new StarSystemGenerator(GALAXY_SEED);
    }

    @Test
    void sameSeedProducesIdenticalStarSystem() {
        StarPosition pos = makeStarPosition(1001L, 100.0, 200.0, 5.0, 0.5f);
        StarSystem a = generator.generate(pos, GalaxyRegion.INNER_RIM);
        StarSystem b = new StarSystemGenerator(GALAXY_SEED).generate(pos, GalaxyRegion.INNER_RIM);

        assertEquals(a.spectralClass, b.spectralClass);
        assertEquals(a.luminosityClass, b.luminosityClass);
        assertEquals(a.temperature, b.temperature, 1e-6f);
        assertEquals(a.luminosity, b.luminosity, 1e-6f);
        assertEquals(a.mass, b.mass, 1e-6f);
    }

    @Test
    void temperatureWithinSpectralClassRange() {
        for (long id = 0; id < 500; id++) {
            StarPosition pos = makeStarPosition(id, id * 10.0, id * 5.0, 0.0, 0.3f);
            StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
            assertTrue(sys.temperature >= sys.spectralClass.tempMin &&
                       sys.temperature <= sys.spectralClass.tempMax,
                "Star " + id + " temp " + sys.temperature +
                " outside range for " + sys.spectralClass);
        }
    }

    @Test
    void luminosityPositive() {
        for (long id = 0; id < 200; id++) {
            StarPosition pos = makeStarPosition(id, id * 7.0, id * 3.0, 0.0, 0.5f);
            StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
            assertTrue(sys.luminosity > 0f, "Star " + id + " has non-positive luminosity");
        }
    }

    @Test
    void habitableZoneDerivedFromLuminosity() {
        StarPosition pos = makeStarPosition(999L, 50.0, 50.0, 0.0, 0.5f);
        StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
        float sqrtLum = (float) Math.sqrt(sys.luminosity);
        assertEquals(sqrtLum * 0.75f, sys.habZoneInner, 1e-5f);
        assertEquals(sqrtLum * 1.77f, sys.habZoneOuter, 1e-5f);
    }

    @Test
    void spectralClassDistributionOverManySamples() {
        int[] counts = new int[SpectralClass.values().length];
        int total = 10000;
        for (long id = 0; id < total; id++) {
            StarPosition pos = makeStarPosition(id, id * 1.1, id * 0.7, 0.0, 0.5f);
            StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
            counts[sys.spectralClass.ordinal()]++;
        }
        for (SpectralClass sc : SpectralClass.values()) {
            float actual = (float) counts[sc.ordinal()] / total;
            float expected = sc.frequency;
            assertTrue(Math.abs(actual - expected) < 0.05f,
                sc + " frequency " + actual + " deviates >5% from expected " + expected);
        }
    }

    @Test
    void coreRegionBoostsHotStars() {
        int coreHotCount = 0;
        int rimHotCount = 0;
        int total = 2000;
        for (long id = 0; id < total; id++) {
            StarPosition pos = makeStarPosition(id, id * 1.3, id * 0.9, 0.0, 0.5f);
            StarSystem core = generator.generate(pos, GalaxyRegion.CORE);
            StarSystem rim = new StarSystemGenerator(GALAXY_SEED).generate(pos, GalaxyRegion.OUTER_RIM);
            if (core.spectralClass == SpectralClass.O || core.spectralClass == SpectralClass.B) coreHotCount++;
            if (rim.spectralClass == SpectralClass.O || rim.spectralClass == SpectralClass.B) rimHotCount++;
        }
        assertTrue(coreHotCount > rimHotCount,
            "Core should have more O/B stars (" + coreHotCount + ") than outer rim (" + rimHotCount + ")");
    }

    private StarPosition makeStarPosition(long id, double x, double y, double z, float density) {
        StarPosition sp = new StarPosition();
        sp.uniqueId = id;
        sp.x = x;
        sp.y = y;
        sp.z = z;
        sp.localDensity = density;
        return sp;
    }
}
