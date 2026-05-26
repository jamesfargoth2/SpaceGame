package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalLayoutGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private OrbitalLayoutGenerator layoutGen;
    private StarSystemGenerator starGen;

    @BeforeEach
    void setUp() {
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
    }

    @Test
    void sameSeedProducesIdenticalLayout() {
        StarSystem sys = generateTestSystem(100L);
        List<OrbitalSlot> a = layoutGen.generate(sys);
        List<OrbitalSlot> b = new OrbitalLayoutGenerator().generate(sys);

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).orbitalRadius, b.get(i).orbitalRadius, 1e-6f);
            assertEquals(a.get(i).zone, b.get(i).zone);
        }
    }

    @Test
    void planetCountWithinSpectralClassRange() {
        for (long id = 0; id < 300; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            assertTrue(orbits.size() >= sys.spectralClass.planetCountMin &&
                       orbits.size() <= sys.spectralClass.planetCountMax,
                "Star " + id + " (" + sys.spectralClass + ") has " + orbits.size() +
                " planets, expected " + sys.spectralClass.planetCountMin +
                "-" + sys.spectralClass.planetCountMax);
        }
    }

    @Test
    void orbitalRadiiIncreaseMonotonically() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (int i = 1; i < orbits.size(); i++) {
                assertTrue(orbits.get(i).orbitalRadius > orbits.get(i - 1).orbitalRadius,
                    "Star " + id + " orbit " + i + " radius " + orbits.get(i).orbitalRadius +
                    " not greater than orbit " + (i - 1) + " radius " + orbits.get(i - 1).orbitalRadius);
            }
        }
    }

    @Test
    void zoneClassificationMatchesHabitableZone() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                if (slot.orbitalRadius < sys.habZoneInner) {
                    assertEquals(OrbitalZone.INNER, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be INNER");
                } else if (slot.orbitalRadius <= sys.habZoneOuter) {
                    assertEquals(OrbitalZone.HABITABLE, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be HABITABLE");
                } else if (slot.orbitalRadius <= sys.frostLine * 3f) {
                    assertEquals(OrbitalZone.OUTER, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be OUTER");
                } else {
                    assertEquals(OrbitalZone.DEEP, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be DEEP");
                }
            }
        }
    }

    @Test
    void orbitalPeriodFollowsKeplerThirdLaw() {
        StarSystem sys = generateTestSystem(42L);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        for (OrbitalSlot slot : orbits) {
            float expected = (float) Math.pow(slot.orbitalRadius, 1.5);
            assertEquals(expected, slot.orbitalPeriod, 1e-4f);
        }
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
