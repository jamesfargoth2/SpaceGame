package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.GravityAffectedComponent;
import com.galacticodyssey.core.components.GravitySourceComponent;
import com.galacticodyssey.core.components.GravityZoneComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GravitySystemTest {

    private Engine engine;
    private GravitySystem gravitySystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        gravitySystem = new GravitySystem();
        engine.addSystem(gravitySystem);
    }

    private Entity createSource(float x, float y, float z, float mass) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        GravitySourceComponent src = new GravitySourceComponent();
        src.mass = mass;
        entity.add(src);

        engine.addEntity(entity);
        return entity;
    }

    private Entity createBody(float x, float y, float z, float mass) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        GravityAffectedComponent affected = new GravityAffectedComponent();
        affected.mass = mass;
        entity.add(affected);

        engine.addEntity(entity);
        return entity;
    }

    private Entity createZone(float x, float y, float z, GravityZoneComponent.Mode mode, float radius) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        GravityZoneComponent zone = new GravityZoneComponent();
        zone.mode = mode;
        zone.radius = radius;
        entity.add(zone);

        engine.addEntity(entity);
        return entity;
    }

    @Test
    void singleSourcePullsBodyToward() {
        createSource(100f, 0f, 0f, 1e13f);
        Entity body = createBody(0f, 0f, 0f, 1f);

        engine.update(1f / 60f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        assertTrue(affected.lastAcceleration.x > 0f,
            "Acceleration should point toward source (+X), got: " + affected.lastAcceleration);
        assertEquals(0f, affected.lastAcceleration.y, 1e-10f);
        assertEquals(0f, affected.lastAcceleration.z, 1e-10f);
    }

    @Test
    void influenceRadiusCulls() {
        Entity src = createSource(100000f, 0f, 0f, 1e13f);
        GravitySourceComponent srcComp = src.getComponent(GravitySourceComponent.class);
        srcComp.influenceRadius = 1000f; // body is at 100000, well beyond

        Entity body = createBody(0f, 0f, 0f, 1f);

        engine.update(1f / 60f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        assertEquals(0f, affected.lastAcceleration.x, 1e-20f);
        assertEquals(0f, affected.lastAcceleration.y, 1e-20f);
        assertEquals(0f, affected.lastAcceleration.z, 1e-20f);
    }

    @Test
    void minRadiusPreventsInfinity() {
        createSource(0f, 0f, 0f, 1e13f);
        Entity body = createBody(0f, 0f, 0f, 1f);

        engine.update(1f / 60f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        assertTrue(Float.isFinite(affected.lastAcceleration.x));
        assertTrue(Float.isFinite(affected.lastAcceleration.y));
        assertTrue(Float.isFinite(affected.lastAcceleration.z));
        // At zero distance, direction is zero so acceleration should be zero
        assertEquals(0f, affected.lastAcceleration.len(), 1e-10f);
    }

    @Test
    void zeroGZoneCancelsGravity() {
        createSource(100f, 0f, 0f, 1e13f);
        createZone(0f, 0f, 0f, GravityZoneComponent.Mode.ZERO_G, 50f);
        Entity body = createBody(0f, 0f, 0f, 1f);

        engine.update(1f / 60f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        assertEquals(0f, affected.lastAcceleration.x, 1e-20f);
        assertEquals(0f, affected.lastAcceleration.y, 1e-20f);
        assertEquals(0f, affected.lastAcceleration.z, 1e-20f);
    }

    @Test
    void constantZoneOverridesGravity() {
        createSource(100f, 0f, 0f, 1e13f);

        Entity zoneEntity = createZone(0f, 0f, 0f, GravityZoneComponent.Mode.CONSTANT, 50f);
        GravityZoneComponent zone = zoneEntity.getComponent(GravityZoneComponent.class);
        zone.constantVector.set(0f, -9.81f, 0f);

        Entity body = createBody(0f, 0f, 0f, 1f);

        engine.update(1f / 60f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        assertEquals(0f, affected.lastAcceleration.x, 1e-6f);
        assertEquals(-9.81f, affected.lastAcceleration.y, 1e-6f);
        assertEquals(0f, affected.lastAcceleration.z, 1e-6f);
    }

    @Test
    void isInZeroGReturnsTrueInsideZone() {
        createSource(1000f, 0f, 0f, 1e17f);
        createZone(0f, 0f, 0f, GravityZoneComponent.Mode.ZERO_G, 50f);

        assertTrue(gravitySystem.isInZeroG(new Vector3(0f, 0f, 0f)));
        // Point outside the zone but close enough to the massive source to feel gravity
        assertFalse(gravitySystem.isInZeroG(new Vector3(60f, 0f, 0f)));
    }

    @Test
    void multipleSourcesAccumulate() {
        createSource(100f, 0f, 0f, 1e13f);
        createSource(0f, 100f, 0f, 1e13f);
        Entity body = createBody(0f, 0f, 0f, 1f);

        engine.update(1f / 60f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        // Both sources at equal distance with equal mass, so X and Y acceleration should be equal
        assertTrue(affected.lastAcceleration.x > 0f, "Should be pulled toward +X");
        assertTrue(affected.lastAcceleration.y > 0f, "Should be pulled toward +Y");
        assertEquals(affected.lastAcceleration.x, affected.lastAcceleration.y, 1e-10f,
            "Equal mass sources at equal distance should produce equal acceleration components");
        assertEquals(0f, affected.lastAcceleration.z, 1e-10f);
    }
}
