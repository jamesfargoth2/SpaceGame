package com.galacticodyssey.ship.fluid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Updates the pendulum-mass slosh model for every fluid tank each tick.
 * Computes effective gravity in the tank frame (real gravity + inertial),
 * derives the slosh natural frequency, and integrates a spring-damper
 * oscillator for the fluid centre-of-mass displacement.
 */
public class SloshSystem extends EntitySystem {

    private static final int PRIORITY = 3;

    /** Gravity threshold below which we treat the environment as zero-g. */
    private static final float ZERO_G_THRESHOLD = 0.01f;

    /** Minimum slosh frequency in zero-g (rad/s). */
    private static final float ZERO_G_FREQUENCY = 0.1f;

    /** Factor used in the cylindrical tank slosh formula. */
    private static final float SLOSH_FACTOR = 1.84f;

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    private final ComponentMapper<SloshTankComponent> tankMapper =
        ComponentMapper.getFor(SloshTankComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    public SloshSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(SloshTankComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (deltaTime <= 0f) return;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            SloshTankComponent tank = tankMapper.get(entity);

            if (tank.fillFraction() < 0.01f) continue;

            TransformComponent transform = transformMapper.get(entity);
            PhysicsBodyComponent physics = physicsMapper.get(entity);

            updateTank(tank, transform, physics, deltaTime);
        }
    }

    private void updateTank(SloshTankComponent tank, TransformComponent transform,
                            PhysicsBodyComponent physics, float dt) {
        Vector3 effectiveG = Pools.obtain(Vector3.class);
        Vector3 equilibrium = Pools.obtain(Vector3.class);
        Vector3 displacement = Pools.obtain(Vector3.class);
        Vector3 accel = Pools.obtain(Vector3.class);
        Vector3 shipAccelLocal = Pools.obtain(Vector3.class);
        Vector3 wallNormal = Pools.obtain(Vector3.class);

        try {
            // Compute ship linear acceleration in local frame.
            // For a rigid body: a = F/m. Bullet stores totalForce.
            computeShipAccelLocal(physics, transform.rotation, shipAccelLocal);

            // Effective gravity in tank frame = gravity - ship acceleration.
            // In space the dominant "gravity" is the ship's own thrust, so
            // effectiveG is simply the inertial pseudo-force from the hull accelerating.
            // Real gravity is negligible for most scenarios; set to zero here.
            effectiveG.set(shipAccelLocal).scl(-1f);

            float effectiveGMag = effectiveG.len();

            // Update natural frequency
            tank.naturalFrequency = computeNaturalFrequency(
                tank.tankRadius, tank.fillFraction(), effectiveGMag);

            // Equilibrium position: fluid settles toward effective-g direction
            if (effectiveGMag > ZERO_G_THRESHOLD) {
                equilibrium.set(effectiveG).nor()
                    .scl(tank.tankRadius * tank.fillFraction());
            } else {
                equilibrium.setZero();
            }

            // Spring-damper: F/m = -omega^2 * x - 2 * zeta * omega * v
            displacement.set(tank.fluidLocalPos).sub(equilibrium);

            float omega = tank.naturalFrequency;
            float zeta = tank.hasBaffles
                ? tank.damping + tank.baffleEfficiency * 2f
                : tank.damping;

            accel.set(displacement).scl(-omega * omega);
            accel.x -= 2f * zeta * omega * tank.fluidVelocity.x;
            accel.y -= 2f * zeta * omega * tank.fluidVelocity.y;
            accel.z -= 2f * zeta * omega * tank.fluidVelocity.z;

            // Integrate velocity and position
            tank.fluidVelocity.mulAdd(accel, dt);
            tank.fluidLocalPos.mulAdd(tank.fluidVelocity, dt);

            // Clamp slosh amplitude to tank radius
            float dist = tank.fluidLocalPos.len();
            if (dist > tank.tankRadius) {
                tank.fluidLocalPos.nor().scl(tank.tankRadius);

                // Wall impact: damp velocity component toward wall
                wallNormal.set(tank.fluidLocalPos).nor();
                float vToWall = tank.fluidVelocity.dot(wallNormal);
                if (vToWall > 0f) {
                    // Reflect and damp the outward velocity (coefficient of restitution ~0.33)
                    tank.fluidVelocity.x -= wallNormal.x * vToWall * 1.5f;
                    tank.fluidVelocity.y -= wallNormal.y * vToWall * 1.5f;
                    tank.fluidVelocity.z -= wallNormal.z * vToWall * 1.5f;
                }
            }
        } finally {
            Pools.free(effectiveG);
            Pools.free(equilibrium);
            Pools.free(displacement);
            Pools.free(accel);
            Pools.free(shipAccelLocal);
            Pools.free(wallNormal);
        }
    }

    /**
     * Computes the slosh natural frequency for a cylindrical/spherical tank.
     * omega = sqrt(g * tanh(1.84 * h / R) * 1.84 / R)
     */
    static float computeNaturalFrequency(float tankRadius, float fillFraction,
                                          float gravityMagnitude) {
        if (gravityMagnitude < ZERO_G_THRESHOLD) {
            return ZERO_G_FREQUENCY;
        }
        float h = fillFraction * 2f * tankRadius;
        float factor = SLOSH_FACTOR / tankRadius;
        float arg = factor * h;
        float tanhVal = (float) Math.tanh(arg);
        return (float) Math.sqrt(gravityMagnitude * tanhVal * factor);
    }

    /**
     * Derives ship linear acceleration in local (body) frame from the Bullet
     * rigid body's total applied force and the ship's orientation.
     */
    private void computeShipAccelLocal(PhysicsBodyComponent physics,
                                       Quaternion rotation, Vector3 out) {
        if (physics == null || physics.body == null || physics.mass <= 0f) {
            out.setZero();
            return;
        }
        // totalForce is in world space; rotate into local frame
        Vector3 force = physics.body.getTotalForce();
        out.set(force);
        out.scl(1f / physics.mass);

        // World-to-local: conjugate of rotation quaternion
        Quaternion inv = Pools.obtain(Quaternion.class);
        try {
            inv.set(rotation).conjugate();
            out.mul(inv);
        } finally {
            Pools.free(inv);
        }
    }
}
