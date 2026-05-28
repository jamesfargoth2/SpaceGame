package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalSlot;
import com.galacticodyssey.galaxy.OrbitalZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalPositionSystemTest {

    private Engine engine;
    private OrbitalPositionSystem positionSystem;
    private Entity starEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        positionSystem = new OrbitalPositionSystem();
        engine.addSystem(positionSystem);

        starEntity = new Entity();
        TransformComponent starTransform = new TransformComponent();
        starTransform.position.set(0f, 0f, 0f);
        starEntity.add(starTransform);

        OrbitalBodyComponent starBody = new OrbitalBodyComponent();
        starBody.bodyType = CelestialBodyType.STAR;
        starBody.orbitalSlot = null;
        starEntity.add(starBody);

        engine.addEntity(starEntity);
    }

    @Test
    void planetPositionedRelativeToStar() {
        OrbitalSlot slot = new OrbitalSlot(0, 1.0f, 0.0f, OrbitalZone.HABITABLE);
        slot.starMass = 1.989e30f;
        slot.currentTrueAnomaly = 0f;

        Entity planet = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planet.add(planetTransform);

        OrbitalBodyComponent planetBody = new OrbitalBodyComponent();
        planetBody.bodyType = CelestialBodyType.PLANET;
        planetBody.orbitalSlot = slot;
        planetBody.parentBody = starEntity;
        planet.add(planetBody);

        engine.addEntity(planet);
        engine.update(0f);

        float expectedX = 1.0f * OrbitalConstants.AU_TO_GAME_UNITS;
        assertEquals(expectedX, planetTransform.position.x, 1f);
        assertEquals(0f, planetTransform.position.y, 0.1f);
    }

    @Test
    void starPositionUnchanged() {
        TransformComponent starTransform = starEntity.getComponent(TransformComponent.class);
        starTransform.position.set(50f, 0f, 0f);

        engine.update(0f);

        assertEquals(50f, starTransform.position.x, 0.01f);
    }

    @Test
    void planetAtHalfOrbitOffset() {
        OrbitalSlot slot = new OrbitalSlot(0, 2.0f, 0.0f, OrbitalZone.OUTER);
        slot.starMass = 1.989e30f;
        slot.currentTrueAnomaly = MathUtils.PI;

        Entity planet = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planet.add(planetTransform);

        OrbitalBodyComponent planetBody = new OrbitalBodyComponent();
        planetBody.bodyType = CelestialBodyType.PLANET;
        planetBody.orbitalSlot = slot;
        planetBody.parentBody = starEntity;
        planet.add(planetBody);

        engine.addEntity(planet);
        engine.update(0f);

        float expectedX = -2.0f * OrbitalConstants.AU_TO_GAME_UNITS;
        assertEquals(expectedX, planetTransform.position.x, 1f);
    }
}
