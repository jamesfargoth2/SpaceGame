package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.DebrisComponent;
import com.galacticodyssey.core.components.DebrisComponent.SimLevel;
import com.galacticodyssey.core.components.TransformComponent;

public class DebrisLODSystem extends IteratingSystem {

    private static final float FULL_PHYSICS_RANGE = 2000f;
    private static final float ORBITAL_ONLY_RANGE = 50000f;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<DebrisComponent> debrisMapper =
        ComponentMapper.getFor(DebrisComponent.class);

    private final Vector3 playerPosition = new Vector3();

    public DebrisLODSystem() {
        super(Family.all(TransformComponent.class, DebrisComponent.class).get(), 0);
    }

    public void setPlayerPosition(Vector3 position) {
        playerPosition.set(position);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = transformMapper.get(entity);
        DebrisComponent debris = debrisMapper.get(entity);

        debris.distanceToPlayer = transform.position.dst(playerPosition);

        if (debris.distanceToPlayer < FULL_PHYSICS_RANGE) {
            debris.simulationLevel = SimLevel.FULL;
        } else if (debris.distanceToPlayer < ORBITAL_ONLY_RANGE) {
            debris.simulationLevel = SimLevel.ORBITAL;
        } else {
            debris.simulationLevel = SimLevel.STATIC;
        }
    }
}
