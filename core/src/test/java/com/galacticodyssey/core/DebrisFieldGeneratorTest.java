package com.galacticodyssey.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.core.components.DebrisComponent;
import com.galacticodyssey.core.components.DebrisComponent.DebrisClass;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebrisFieldGeneratorTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = new Engine();
    }

    @Test
    void createAsteroidSetsComponents() {
        Vector3 pos = new Vector3(100f, 200f, 300f);
        Entity entity = DebrisFieldGenerator.createAsteroid(engine, pos, 5000f, 25f);

        TransformComponent transform = entity.getComponent(TransformComponent.class);
        assertNotNull(transform);
        assertEquals(100f, transform.position.x, 0.01f);
        assertEquals(200f, transform.position.y, 0.01f);
        assertEquals(300f, transform.position.z, 0.01f);

        DebrisComponent debris = entity.getComponent(DebrisComponent.class);
        assertNotNull(debris);
        assertEquals(5000f, debris.mass, 0.01f);
        assertEquals(25f, debris.radius, 0.01f);
    }

    @Test
    void classificationByRadius() {
        assertEquals(DebrisClass.DUST, DebrisFieldGenerator.classifyByRadius(0.5f));
        assertEquals(DebrisClass.PEBBLE, DebrisFieldGenerator.classifyByRadius(3f));
        assertEquals(DebrisClass.ROCK, DebrisFieldGenerator.classifyByRadius(25f));
        assertEquals(DebrisClass.BOULDER, DebrisFieldGenerator.classifyByRadius(100f));
        assertEquals(DebrisClass.ASTEROID, DebrisFieldGenerator.classifyByRadius(1000f));
        assertEquals(DebrisClass.PLANETOID, DebrisFieldGenerator.classifyByRadius(60000f));
    }

    @Test
    void generateBeltCreatesEntities() {
        int count = 50;
        Array<Entity> belt = DebrisFieldGenerator.generateBelt(
            engine, 1000f, 5000f, 0.1f, count, 42L);
        assertEquals(count, belt.size);
    }

    @Test
    void beltAsteroidsHaveOrbitalVelocity() {
        Array<Entity> belt = DebrisFieldGenerator.generateBelt(
            engine, 10000f, 20000f, 0.05f, 20, 123L);

        for (Entity entity : belt) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            DebrisComponent debris = entity.getComponent(DebrisComponent.class);

            float speed = debris.velocity.len();
            assertTrue(speed > 0f, "Orbital velocity should be non-zero");

            Vector3 radial = new Vector3(transform.position.x, 0f, transform.position.z).nor();
            Vector3 velXZ = new Vector3(debris.velocity.x, 0f, debris.velocity.z).nor();
            float dot = Math.abs(radial.dot(velXZ));
            assertTrue(dot < 0.15f,
                "Velocity should be roughly perpendicular to radial direction, dot=" + dot);
        }
    }
}
