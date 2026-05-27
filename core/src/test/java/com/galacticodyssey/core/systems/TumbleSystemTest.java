package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.DebrisComponent;
import com.galacticodyssey.core.components.DebrisComponent.DebrisClass;
import com.galacticodyssey.core.components.DebrisComponent.SimLevel;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TumbleSystemTest {

    private Engine engine;
    private TumbleSystem tumbleSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        tumbleSystem = new TumbleSystem();
        engine.addSystem(tumbleSystem);
    }

    private Entity createDebrisEntity(DebrisClass debrisClass, SimLevel simLevel) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        DebrisComponent debris = new DebrisComponent();
        debris.debrisClass = debrisClass;
        debris.simulationLevel = simLevel;
        entity.add(transform);
        entity.add(debris);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    void rockTumbles() {
        Entity entity = createDebrisEntity(DebrisClass.ROCK, SimLevel.FULL);
        DebrisComponent debris = entity.getComponent(DebrisComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);

        debris.angularVelocity.set(0.1f, 0.2f, 0.05f);
        Quaternion initialRotation = new Quaternion(transform.rotation);

        for (int i = 0; i < 10; i++) {
            engine.update(1f / 60f);
        }

        assertFalse(
            Math.abs(transform.rotation.x - initialRotation.x) < 1e-6f &&
            Math.abs(transform.rotation.y - initialRotation.y) < 1e-6f &&
            Math.abs(transform.rotation.z - initialRotation.z) < 1e-6f &&
            Math.abs(transform.rotation.w - initialRotation.w) < 1e-6f,
            "Rotation should have changed after tumble updates"
        );
    }

    @Test
    void dustNotSimulated() {
        Entity entity = createDebrisEntity(DebrisClass.DUST, SimLevel.FULL);
        DebrisComponent debris = entity.getComponent(DebrisComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);

        debris.angularVelocity.set(0.5f, 0.5f, 0.5f);
        debris.velocity.set(10f, 0f, 0f);
        Quaternion initialRotation = new Quaternion(transform.rotation);
        Vector3 initialPosition = new Vector3(transform.position);

        engine.update(1f / 60f);

        assertEquals(initialRotation.x, transform.rotation.x, 1e-6f);
        assertEquals(initialRotation.y, transform.rotation.y, 1e-6f);
        assertEquals(initialRotation.z, transform.rotation.z, 1e-6f);
        assertEquals(initialRotation.w, transform.rotation.w, 1e-6f);
        assertEquals(initialPosition.x, transform.position.x, 1e-6f);
    }

    @Test
    void eulerEquationsConserveEnergy() {
        Entity entity = createDebrisEntity(DebrisClass.ROCK, SimLevel.FULL);
        DebrisComponent debris = entity.getComponent(DebrisComponent.class);

        debris.inertiaTensor.set(2f, 3f, 5f);
        debris.angularVelocity.set(0.3f, 0.2f, 0.1f);

        float initialKE = computeRotationalKE(debris);

        float dt = 1f / 120f;
        for (int i = 0; i < 100; i++) {
            engine.update(dt);
        }

        float finalKE = computeRotationalKE(debris);
        float relativeError = Math.abs(finalKE - initialKE) / initialKE;
        assertTrue(relativeError < 0.01f,
            "Rotational KE should be conserved within 1%, got " + (relativeError * 100f) + "% error");
    }

    @Test
    void positionIntegrates() {
        Entity entity = createDebrisEntity(DebrisClass.ROCK, SimLevel.FULL);
        DebrisComponent debris = entity.getComponent(DebrisComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);

        debris.velocity.set(10f, 20f, 30f);
        transform.position.set(0, 0, 0);

        float dt = 1f / 60f;
        engine.update(dt);

        assertEquals(10f * dt, transform.position.x, 1e-4f);
        assertEquals(20f * dt, transform.position.y, 1e-4f);
        assertEquals(30f * dt, transform.position.z, 1e-4f);
    }

    private float computeRotationalKE(DebrisComponent debris) {
        float wx = debris.angularVelocity.x;
        float wy = debris.angularVelocity.y;
        float wz = debris.angularVelocity.z;
        return 0.5f * (debris.inertiaTensor.x * wx * wx
                     + debris.inertiaTensor.y * wy * wy
                     + debris.inertiaTensor.z * wz * wz);
    }
}
