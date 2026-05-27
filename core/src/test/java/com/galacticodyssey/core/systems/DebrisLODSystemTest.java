package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.DebrisComponent;
import com.galacticodyssey.core.components.DebrisComponent.SimLevel;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebrisLODSystemTest {

    private Engine engine;
    private DebrisLODSystem lodSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        lodSystem = new DebrisLODSystem();
        engine.addSystem(lodSystem);
        lodSystem.setPlayerPosition(new Vector3(0, 0, 0));
    }

    private Entity createDebrisAt(float x, float y, float z) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        DebrisComponent debris = new DebrisComponent();
        entity.add(transform);
        entity.add(debris);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    void nearPlayerIsFull() {
        Entity entity = createDebrisAt(500f, 0f, 0f);
        engine.update(0f);
        assertEquals(SimLevel.FULL, entity.getComponent(DebrisComponent.class).simulationLevel);
    }

    @Test
    void midRangeIsOrbital() {
        Entity entity = createDebrisAt(10000f, 0f, 0f);
        engine.update(0f);
        assertEquals(SimLevel.ORBITAL, entity.getComponent(DebrisComponent.class).simulationLevel);
    }

    @Test
    void farAwayIsStatic() {
        Entity entity = createDebrisAt(100000f, 0f, 0f);
        engine.update(0f);
        assertEquals(SimLevel.STATIC, entity.getComponent(DebrisComponent.class).simulationLevel);
    }

    @Test
    void distanceUpdated() {
        Entity entity = createDebrisAt(1234f, 0f, 0f);
        engine.update(0f);
        assertEquals(1234f, entity.getComponent(DebrisComponent.class).distanceToPlayer, 0.1f);
    }
}
