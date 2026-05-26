package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

public class ShipInteriorPhysicsSystem extends IteratingSystem {

    private static final float FIXED_TIMESTEP = 1f / 60f;
    private static final int MAX_SUBSTEPS = 3;

    private final ComponentMapper<ShipInteriorComponent> interiorMapper =
        ComponentMapper.getFor(ShipInteriorComponent.class);

    public ShipInteriorPhysicsSystem() {
        super(Family.all(ShipInteriorComponent.class).get(), 3);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipInteriorComponent interior = interiorMapper.get(entity);
        if (!interior.active || interior.interiorWorld == null) return;

        interior.interiorWorld.stepSimulation(deltaTime, MAX_SUBSTEPS, FIXED_TIMESTEP);
    }
}
