package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.GravityAffectedComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GravityForceSystemTest {

    @Test
    void nullBodyDoesNotCrash() {
        Engine engine = new Engine();
        GravityForceSystem system = new GravityForceSystem();
        engine.addSystem(system);

        Entity entity = new Entity();
        entity.add(new TransformComponent());

        GravityAffectedComponent affected = new GravityAffectedComponent();
        affected.mass = 100f;
        affected.lastAcceleration.set(0f, -9.8f, 0f);
        entity.add(affected);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.mass = 100f;
        entity.add(physics);

        engine.addEntity(entity);
        assertDoesNotThrow(() -> engine.update(0.016f));
    }
}
