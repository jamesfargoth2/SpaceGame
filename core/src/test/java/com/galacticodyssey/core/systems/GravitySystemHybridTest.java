package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GravitySystemHybridTest {

    private Engine engine;
    private GravitySystem gravitySystem;
    private Entity starEntity;
    private Entity planetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        gravitySystem = new GravitySystem();
        engine.addSystem(gravitySystem);

        starEntity = createGravitySource(0f, 0f, 0f, 1.989e30f, 500000f);
        OrbitalBodyComponent starOrbital = new OrbitalBodyComponent();
        starOrbital.bodyType = CelestialBodyType.STAR;
        starOrbital.soiRadius = 0f;
        starEntity.add(starOrbital);
        engine.addEntity(starEntity);

        planetEntity = createGravitySource(1000f, 0f, 0f, 5.972e24f, 200f);
        OrbitalBodyComponent planetOrbital = new OrbitalBodyComponent();
        planetOrbital.bodyType = CelestialBodyType.PLANET;
        planetOrbital.soiRadius = 200f;
        planetEntity.add(planetOrbital);
        engine.addEntity(planetEntity);
    }

    @Test
    void entityWithSOITrackerFeelsDominantAtFullStrength() {
        Entity ship = createAffectedBody(1050f, 0f, 0f, 1000f);
        SOITrackerComponent soi = new SOITrackerComponent();
        soi.dominantBody = planetEntity;
        soi.secondaryBody = starEntity;
        soi.distanceToDominant = 50f;
        ship.add(soi);
        engine.addEntity(ship);

        engine.update(0.016f);

        GravityAffectedComponent affected = ship.getComponent(GravityAffectedComponent.class);
        assertTrue(affected.lastAcceleration.len() > 0f, "Ship should feel gravity");

        Vector3 toPlanet = new Vector3(1000f, 0f, 0f).sub(1050f, 0f, 0f).nor();
        float dot = affected.lastAcceleration.cpy().nor().dot(toPlanet);
        assertTrue(dot > 0.5f, "Gravity should pull predominantly toward dominant body (planet)");
    }

    @Test
    void entityWithoutSOITrackerFeelsAllSourcesEqually() {
        Entity body = createAffectedBody(500f, 0f, 0f, 1f);
        engine.addEntity(body);

        engine.update(0.016f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        assertTrue(affected.lastAcceleration.len() > 0f);
    }

    private Entity createGravitySource(float x, float y, float z, float mass, float influence) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);
        GravitySourceComponent source = new GravitySourceComponent();
        source.mass = mass;
        source.influenceRadius = influence;
        entity.add(source);
        return entity;
    }

    private Entity createAffectedBody(float x, float y, float z, float mass) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);
        GravityAffectedComponent affected = new GravityAffectedComponent();
        affected.mass = mass;
        entity.add(affected);
        return entity;
    }
}
