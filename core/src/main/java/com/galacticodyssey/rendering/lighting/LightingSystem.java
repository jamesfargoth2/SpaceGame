package com.galacticodyssey.rendering.lighting;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;

public class LightingSystem extends EntitySystem {

    private static final ComponentMapper<LightComponent> lightMapper =
        ComponentMapper.getFor(LightComponent.class);

    private ImmutableArray<Entity> lightEntities;

    @Override
    public void addedToEngine(Engine engine) {
        lightEntities = engine.getEntitiesFor(Family.all(LightComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        lightEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        // Light collection is passive — no per-frame logic needed.
    }

    public ImmutableArray<Entity> getLights() {
        return lightEntities;
    }

    public static LightComponent getLight(Entity entity) {
        return lightMapper.get(entity);
    }
}
