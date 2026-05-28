package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.GravityAffectedComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

public class GravityForceSystem extends EntitySystem {

    private static final ComponentMapper<GravityAffectedComponent> affectedMapper =
        ComponentMapper.getFor(GravityAffectedComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private ImmutableArray<Entity> bodies;

    public GravityForceSystem() {
        super(5);
    }

    @Override
    public void addedToEngine(Engine engine) {
        bodies = engine.getEntitiesFor(
            Family.all(GravityAffectedComponent.class, PhysicsBodyComponent.class,
                       TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        bodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (bodies == null) return;

        Vector3 force = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < bodies.size(); i++) {
                Entity entity = bodies.get(i);
                GravityAffectedComponent affected = affectedMapper.get(entity);
                PhysicsBodyComponent physics = physicsMapper.get(entity);

                if (physics.body == null) continue;

                force.set(affected.lastAcceleration).scl(physics.mass);
                physics.body.applyCentralForce(force);
            }
        } finally {
            Pools.free(force);
        }
    }
}
