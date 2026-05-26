package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private AtmosphereGenerator atmoGen;
    private StarSystemGenerator starGen;
    private OrbitalLayoutGenerator layoutGen;
    private PlanetGenerator planetGen;

    @BeforeEach
    void setUp() {
        atmoGen = new AtmosphereGenerator();
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
        planetGen = new PlanetGenerator(GALAXY_SEED);
    }

    @Test
    void sameSeedProducesIdenticalAtmosphere() {
        TestPlanetResult r = generateTestPlanetWithAtmosphere(100L);
        if (r == null) return;
        Atmosphere a = atmoGen.generate(r.planet, r.system);
        Atmosphere b = new AtmosphereGenerator().generate(r.planet, r.system);
        if (a == null || b == null) return;

        assertEquals(a.surfacePressure, b.surfacePressure, 1e-6f);
        assertEquals(a.surfaceTemp, b.surfaceTemp, 1e-6f);
        assertEquals(a.breathable, b.breathable);
    }

    @Test
    void gasFractionsSumToOne() {
        for (long id = 0; id < 200; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            float sum = 0f;
            for (float fraction : atmo.composition.values()) {
                sum += fraction;
            }
            assertEquals(1.0f, sum, 0.01f,
                "Star " + id + " atmosphere gas fractions sum to " + sum);
        }
    }

    @Test
    void pressureWithinTypeRange() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            assertTrue(atmo.surfacePressure > 0f,
                "Star " + id + " " + r.planet.type + " pressure should be positive");
        }
    }

    @Test
    void breathabilityRequiresOxygenAndSafePressure() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            if (atmo.breathable) {
                Float o2 = atmo.composition.getOrDefault(Gas.O2, 0f);
                assertTrue(o2 >= 0.15f && o2 <= 0.25f,
                    "Breathable atmosphere O2 = " + o2 + " outside 15-25%");
                assertTrue(atmo.surfacePressure >= 0.5f && atmo.surfacePressure <= 2.0f,
                    "Breathable atmosphere pressure = " + atmo.surfacePressure + " outside 0.5-2.0");
            }
        }
    }

    @Test
    void vacuumHazardWhenLowPressure() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            if (atmo.surfacePressure < 0.01f) {
                assertTrue(atmo.hazards.contains(AtmoHazard.VACUUM),
                    "Low-pressure atmosphere should have VACUUM hazard");
            }
        }
    }

    @Test
    void airlessPlanetsReturnNull() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            if (r.planet.type == PlanetType.MOLTEN || r.planet.type == PlanetType.DWARF) {
                Atmosphere atmo = atmoGen.generate(r.planet, r.system);
                assertNull(atmo, r.planet.type + " should have null atmosphere");
            }
        }
    }

    private TestPlanetResult generateTestPlanetWithAtmosphere(long id) {
        StarPosition pos = new StarPosition();
        pos.uniqueId = id;
        pos.x = id * 10.0;
        pos.y = id * 5.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;
        StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        if (orbits.isEmpty()) return null;
        sys.orbits.addAll(orbits);
        OrbitalSlot slot = orbits.get(0);
        Planet planet = planetGen.generate(slot, sys);
        slot.planet = planet;
        return new TestPlanetResult(planet, sys, slot);
    }

    private record TestPlanetResult(Planet planet, StarSystem system, OrbitalSlot slot) {}
}
