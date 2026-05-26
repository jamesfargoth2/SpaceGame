package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlanetGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private PlanetGenerator planetGen;
    private StarSystemGenerator starGen;
    private OrbitalLayoutGenerator layoutGen;

    @BeforeEach
    void setUp() {
        planetGen = new PlanetGenerator(GALAXY_SEED);
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
    }

    @Test
    void sameSeedProducesIdenticalPlanet() {
        StarSystem sys = generateTestSystem(100L);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        OrbitalSlot slot = orbits.get(0);
        Planet a = planetGen.generate(slot, sys);
        Planet b = new PlanetGenerator(GALAXY_SEED).generate(slot, sys);

        assertEquals(a.type, b.type);
        assertEquals(a.radius, b.radius, 1e-6f);
        assertEquals(a.mass, b.mass, 1e-6f);
        assertEquals(a.moons.size(), b.moons.size());
    }

    @Test
    void planetTypeValidForOrbitalZone() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                assertTrue(p.type.validZones.contains(slot.zone),
                    "Star " + id + " orbit " + slot.index + ": " + p.type +
                    " not valid for zone " + slot.zone);
            }
        }
    }

    @Test
    void radiusWithinTypeRange() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                assertTrue(p.radius >= p.type.radiusMin && p.radius <= p.type.radiusMax,
                    "Star " + id + " planet radius " + p.radius +
                    " outside range for " + p.type);
            }
        }
    }

    @Test
    void moonCountWithinTypeRange() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                assertTrue(p.moons.size() >= p.type.moonMin && p.moons.size() <= p.type.moonMax,
                    "Star " + id + " " + p.type + " has " + p.moons.size() +
                    " moons, expected " + p.type.moonMin + "-" + p.type.moonMax);
            }
        }
    }

    @Test
    void moonsAreBarrenOrIceWorld() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                for (Moon moon : p.moons) {
                    assertTrue(moon.type == PlanetType.BARREN || moon.type == PlanetType.ICE_WORLD,
                        "Moon type " + moon.type + " should be BARREN or ICE_WORLD");
                }
            }
        }
    }

    @Test
    void gravityDerivedFromMassAndRadius() {
        StarSystem sys = generateTestSystem(42L);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        Planet p = planetGen.generate(orbits.get(0), sys);
        assertEquals(p.mass / (p.radius * p.radius), p.surfaceGravity, 1e-5f);
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
