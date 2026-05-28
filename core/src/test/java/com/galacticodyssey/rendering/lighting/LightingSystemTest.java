package com.galacticodyssey.rendering.lighting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightingSystemTest {

    private Engine engine;
    private LightingSystem lightingSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        lightingSystem = new LightingSystem();
        engine.addSystem(lightingSystem);
    }

    @Test
    void collectsEntitiesWithLightComponent() {
        Entity entity = new Entity();
        LightComponent light = new LightComponent();
        light.type = LightComponent.Type.POINT;
        entity.add(light);
        engine.addEntity(entity);
        assertEquals(1, lightingSystem.getLights().size());
    }

    @Test
    void ignoresEntitiesWithoutLightComponent() {
        Entity entity = new Entity();
        engine.addEntity(entity);
        assertEquals(0, lightingSystem.getLights().size());
    }

    @Test
    void removedEntityDisappearsFromLights() {
        Entity entity = new Entity();
        entity.add(new LightComponent());
        engine.addEntity(entity);
        assertEquals(1, lightingSystem.getLights().size());
        engine.removeEntity(entity);
        assertEquals(0, lightingSystem.getLights().size());
    }
}
