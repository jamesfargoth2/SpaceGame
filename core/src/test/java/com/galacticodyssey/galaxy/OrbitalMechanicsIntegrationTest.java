package com.galacticodyssey.galaxy;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.*;
import com.galacticodyssey.core.systems.GravitySystem;
import com.galacticodyssey.core.systems.OrbitalPositionSystem;
import com.galacticodyssey.core.systems.SOITrackingSystem;
import com.galacticodyssey.core.systems.TrajectoryPredictionSystem;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalMechanicsIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private KeplerianOrbitSystem keplerSystem;
    private Entity starEntity;
    private Entity planetEntity;
    private StarSystem starSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        engine.addSystem(new GravitySystem());
        keplerSystem = new KeplerianOrbitSystem(eventBus);
        engine.addSystem(keplerSystem);
        engine.addSystem(new OrbitalPositionSystem());
        engine.addSystem(new SOITrackingSystem(eventBus));
        engine.addSystem(new TrajectoryPredictionSystem());

        starSystem = new StarSystem(1L, 42L, SpectralClass.G, LuminosityClass.MAIN_SEQUENCE,
            5778f, 1.0f, 1.0f, 1.0f, 4.6f, Color.YELLOW);

        OrbitalSlot slot = new OrbitalSlot(0, 1.0f, 0.0f, OrbitalZone.HABITABLE);
        Planet planet = new Planet(100L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23.5f, false);
        slot.planet = planet;
        starSystem.orbits.add(slot);

        starEntity = new Entity();
        TransformComponent starTransform = new TransformComponent();
        starEntity.add(starTransform);
        GravitySourceComponent starGravity = new GravitySourceComponent();
        starGravity.mass = OrbitalConstants.SOLAR_MASS_KG;
        starGravity.influenceRadius = 500000f;
        starEntity.add(starGravity);
        OrbitalBodyComponent starOrbital = new OrbitalBodyComponent();
        starOrbital.bodyType = CelestialBodyType.STAR;
        starOrbital.soiRadius = 0f;
        starEntity.add(starOrbital);
        engine.addEntity(starEntity);

        float planetMassKg = 1.0f * OrbitalConstants.EARTH_MASS_KG;
        float planetSOI = OrbitalMechanics.sphereOfInfluence(
            1.0f * OrbitalConstants.AU_TO_GAME_UNITS, planetMassKg, OrbitalConstants.SOLAR_MASS_KG);

        planetEntity = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planetEntity.add(planetTransform);
        GravitySourceComponent planetGravity = new GravitySourceComponent();
        planetGravity.mass = planetMassKg;
        planetGravity.influenceRadius = planetSOI;
        planetEntity.add(planetGravity);
        OrbitalBodyComponent planetOrbital = new OrbitalBodyComponent();
        planetOrbital.bodyType = CelestialBodyType.PLANET;
        planetOrbital.orbitalSlot = slot;
        planetOrbital.parentBody = starEntity;
        planetOrbital.soiRadius = planetSOI;
        planetEntity.add(planetOrbital);
        engine.addEntity(planetEntity);

        keplerSystem.setActiveSystem(starSystem);
    }

    @Test
    void planetMovesAfterOrbitTicks() {
        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);
        engine.update(0f);
        float initialX = planetTransform.position.x;

        // The 1 AU slot has an orbital period of 1.0 game-time units, so any whole-orbit
        // multiple would land the planet back on its start point. Advance a quarter orbit
        // instead so the motion is unambiguous.
        keplerSystem.setTimeScale(0.25f);
        engine.update(1.0f);

        assertNotEquals(initialX, planetTransform.position.x, 1f,
            "Planet should have moved after orbit advancement");
    }

    @Test
    void planetStaysAtCorrectDistanceFromStar() {
        engine.update(0f);

        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);
        TransformComponent starTransform = starEntity.getComponent(TransformComponent.class);

        float dist = planetTransform.position.cpy().sub(starTransform.position).len();
        float expectedDist = 1.0f * OrbitalConstants.AU_TO_GAME_UNITS;

        assertEquals(expectedDist, dist, expectedDist * 0.01f);
    }

    @Test
    void shipInsidePlanetSOIGetsPlanetAsDominant() {
        engine.update(0f);

        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);
        float planetSOI = planetEntity.getComponent(OrbitalBodyComponent.class).soiRadius;

        Entity ship = new Entity();
        TransformComponent shipTransform = new TransformComponent();
        // Place the ship well inside the planet's sphere of influence.
        shipTransform.position.set(planetTransform.position).add(planetSOI * 0.5f, 0f, 0f);
        ship.add(shipTransform);
        ship.add(new SOITrackerComponent());
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertSame(planetEntity, tracker.dominantBody,
            "Ship near planet should have planet as dominant body");
    }

    @Test
    void fullOrbitReturnsToStartPosition() {
        OrbitalSlot slot = starSystem.orbits.get(0);
        engine.update(0f);

        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);
        float startX = planetTransform.position.x;
        float startZ = planetTransform.position.z;

        float period = slot.orbitalPeriod;
        int steps = 1000;
        float dtPerStep = period / steps;
        for (int i = 0; i < steps; i++) {
            keplerSystem.update(dtPerStep);
        }
        engine.getSystem(OrbitalPositionSystem.class).update(0f);

        assertEquals(startX, planetTransform.position.x, OrbitalConstants.AU_TO_GAME_UNITS * 0.02f,
            "Planet should return near start X after one full period");
        assertEquals(startZ, planetTransform.position.z, OrbitalConstants.AU_TO_GAME_UNITS * 0.02f,
            "Planet should return near start Z after one full period");
    }
}
