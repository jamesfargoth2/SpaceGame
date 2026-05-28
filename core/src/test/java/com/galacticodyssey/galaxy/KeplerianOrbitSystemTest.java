package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.Moon;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeplerianOrbitSystemTest {

    private EventBus eventBus;
    private KeplerianOrbitSystem system;
    private StarSystem starSystem;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        system = new KeplerianOrbitSystem(eventBus);

        starSystem = new StarSystem(1L, 42L, SpectralClass.G, LuminosityClass.MAIN_SEQUENCE,
            5778f, 1.0f, 1.0f, 1.0f, 4.6f, Color.YELLOW);
        OrbitalSlot slot = new OrbitalSlot(0, 1.0f, 0.017f, OrbitalZone.HABITABLE);
        starSystem.orbits.add(slot);

        Planet planet = new Planet(100L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23.5f, false);
        Moon moon = new Moon(200L, PlanetType.BARREN, 0.27f, 0.012f,
            0.00257f, 0.055f, 0.09f);
        moon.computeOrbitalPeriod(1.0f * OrbitalConstants.EARTH_MASS_KG);
        planet.moons.add(moon);
        slot.planet = planet;

        system.setActiveSystem(starSystem);
    }

    @Test
    void timeScaleDefaultsToOne() {
        assertEquals(1.0f, system.getTimeScale());
    }

    @Test
    void timeScaleMultipliesDt() {
        system.setTimeScale(10f);
        OrbitalSlot slot = starSystem.orbits.get(0);
        float initialAnomaly = slot.currentMeanAnomaly;

        system.update(1.0f);

        float n = MathUtils.PI2 / slot.orbitalPeriod;
        float expected = initialAnomaly + n * 10f;
        assertEquals(expected, slot.currentMeanAnomaly, 1e-5f);
    }

    @Test
    void moonAnomalyAdvancesEachTick() {
        Planet planet = starSystem.orbits.get(0).planet;
        Moon moon = planet.moons.get(0);
        float initialMoonAnomaly = moon.currentMeanAnomaly;

        system.update(1.0f);

        assertTrue(moon.currentMeanAnomaly > initialMoonAnomaly,
            "Moon mean anomaly should advance after update");
    }

    @Test
    void orbitTickEventPublished() {
        List<KeplerianOrbitSystem.OrbitTickEvent> received = new ArrayList<>();
        eventBus.subscribe(KeplerianOrbitSystem.OrbitTickEvent.class, received::add);

        system.update(0.016f);

        assertEquals(1, received.size());
        assertSame(starSystem, received.get(0).system);
    }
}
