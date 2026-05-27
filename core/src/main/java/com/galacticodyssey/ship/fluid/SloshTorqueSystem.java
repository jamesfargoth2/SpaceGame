package com.galacticodyssey.ship.fluid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Computes the reactive torque that sloshing fluid exerts on the parent
 * ship and applies it to the ship's Bullet rigid body. Runs after
 * {@link SloshSystem} so that fluid positions and velocities are current.
 */
public class SloshTorqueSystem extends EntitySystem {

    private static final int PRIORITY = 4;

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    private final ComponentMapper<SloshTankComponent> tankMapper =
        ComponentMapper.getFor(SloshTankComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    public SloshTorqueSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(SloshTankComponent.class, PhysicsBodyComponent.class,
                       TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (deltaTime <= 0f) return;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            SloshTankComponent tank = tankMapper.get(entity);

            if (tank.fillFraction() < 0.01f) continue;

            PhysicsBodyComponent physics = physicsMapper.get(entity);
            TransformComponent transform = transformMapper.get(entity);
            if (physics == null || physics.body == null) continue;

            applySloshTorque(tank, physics, transform.rotation, deltaTime);
        }
    }

    /**
     * Computes slosh force from the fluid's acceleration and derives
     * the torque about the ship's centre of mass via cross product
     * of the tank offset and slosh force.
     */
    private void applySloshTorque(SloshTankComponent tank, PhysicsBodyComponent physics,
                                  Quaternion rotation, float dt) {
        Vector3 sloshForce = Pools.obtain(Vector3.class);
        Vector3 torqueLocal = Pools.obtain(Vector3.class);
        Vector3 torqueWorld = Pools.obtain(Vector3.class);

        try {
            // Slosh force = fluidMass * (fluidVelocity / dt)
            // This is the approximate force the fluid exerts on the tank wall.
            float invDt = 1f / dt;
            sloshForce.set(tank.fluidVelocity).scl(tank.fluidMass * invDt);

            // Torque = tankCentre x sloshForce (local space)
            torqueLocal.set(tank.tankCentre).crs(sloshForce);

            // Rotate torque to world space for Bullet
            torqueWorld.set(torqueLocal).mul(rotation);

            physics.body.applyTorque(torqueWorld);
        } finally {
            Pools.free(sloshForce);
            Pools.free(torqueLocal);
            Pools.free(torqueWorld);
        }
    }
}
