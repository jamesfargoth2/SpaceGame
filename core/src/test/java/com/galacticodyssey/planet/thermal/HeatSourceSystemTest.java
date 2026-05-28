package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeatSourceSystemTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new HeatSourceSystem());
    }

    private Entity emitter(float x, float power, float radius) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        tr.position.set(x, 0, 0);
        HeatSourceComponent h = new HeatSourceComponent();
        h.power = power; h.radius = radius; h.cone = false;
        e.add(tr); e.add(h);
        engine.addEntity(e);
        return e;
    }

    private Entity target(float x) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        tr.position.set(x, 0, 0);
        e.add(tr); e.add(new TemperatureComponent());
        engine.addEntity(e);
        return e;
    }

    @Test
    void depositsHeatIntoInRangeTarget() {
        emitter(0f, 10_000f, 5f);
        Entity tgt = target(2f); // within radius
        engine.update(0.016f);
        assertTrue(tgt.getComponent(TemperatureComponent.class).incomingHeat > 0f);
    }

    @Test
    void ignoresOutOfRangeTarget() {
        emitter(0f, 10_000f, 5f);
        Entity tgt = target(20f); // outside radius
        engine.update(0.016f);
        assertEquals(0f, tgt.getComponent(TemperatureComponent.class).incomingHeat, 0.0001f);
    }

    @Test
    void transientEmitterExpires() {
        Entity e = emitter(0f, 10_000f, 5f);
        e.getComponent(HeatSourceComponent.class).lifetime = 0.01f;
        engine.update(0.016f); // consumes lifetime
        Entity tgt = target(2f);
        engine.update(0.016f); // emitter now expired -> no deposit
        assertEquals(0f, tgt.getComponent(TemperatureComponent.class).incomingHeat, 0.0001f);
    }
}
