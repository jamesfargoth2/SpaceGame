package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.BuoyancySamplePoint;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.components.BoatInputComponent;
import com.galacticodyssey.water.components.BoatMotorComponent;

/**
 * Reads {@link BoatInputComponent} and applies thrust and rudder forces to
 * surface vessels through their {@link PhysicsBodyComponent}'s Bullet rigid body.
 * <p>
 * Throttle and rudder inputs are smoothed via exponential approach at configurable
 * response rates, giving the controls a weighty, analog feel rather than instant
 * digital response. This is what makes the boat feel realistic and heavy.
 * <p>
 * Rudder effectiveness scales with forward speed: at standstill the rudder
 * produces minimal turning force, simulating a real boat's need for water flow
 * past the rudder to generate lift. A minimum effectiveness of 10% prevents the
 * boat from being completely unsteerable at rest.
 * <p>
 * Thrust is only applied when sample points are submerged (the propeller must
 * be in the water). This prevents the boat from accelerating when airborne.
 * <p>
 * Runs at priority 12 (after drag at 11).
 */
public class BoatMotorSystem extends EntitySystem {

    private static final ComponentMapper<BoatInputComponent> inputMapper =
        ComponentMapper.getFor(BoatInputComponent.class);
    private static final ComponentMapper<BoatMotorComponent> motorMapper =
        ComponentMapper.getFor(BoatMotorComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<HullComponent> hullMapper =
        ComponentMapper.getFor(HullComponent.class);

    private ImmutableArray<Entity> boats;

    // Scratch transform for extracting hull axes
    private final Matrix4 tempTransform = new Matrix4();

    public BoatMotorSystem() {
        super(12);
    }

    @Override
    public void addedToEngine(Engine engine) {
        boats = engine.getEntitiesFor(Family.all(
            BoatInputComponent.class,
            BoatMotorComponent.class,
            PhysicsBodyComponent.class,
            HullComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < boats.size(); i++) {
            processEntity(boats.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        final BoatInputComponent input = inputMapper.get(entity);
        final BoatMotorComponent motor = motorMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        final HullComponent hull = hullMapper.get(entity);
        if (physics.body == null) return;

        // Only apply thrust when the hull is in the water
        if (!hasSubmergedPoints(hull)) return;

        // Smooth throttle and rudder inputs for weighty analog feel
        motor.currentThrottle = expApproach(motor.currentThrottle, input.throttle,
                                             motor.throttleResponseRate, dt);
        motor.currentRudder = expApproach(motor.currentRudder, input.steering,
                                           motor.rudderResponseRate, dt);

        physics.body.getWorldTransform(tempTransform);

        Vector3 forward = Pools.obtain(Vector3.class);
        Vector3 thrustForce = Pools.obtain(Vector3.class);
        Vector3 rudderTorque = Pools.obtain(Vector3.class);
        Vector3 linearVel = Pools.obtain(Vector3.class);

        try {
            // Hull forward direction (local -Z convention, matching ship flight)
            forward.set(0f, 0f, -1f).rot(tempTransform).nor();

            // Thrust magnitude: forward or reverse
            float thrustMag;
            if (motor.currentThrottle >= 0f) {
                thrustMag = motor.currentThrottle * motor.maxThrust;
            } else {
                thrustMag = motor.currentThrottle * motor.maxReverseThrust;
            }

            // Apply thrust along hull forward axis
            thrustForce.set(forward).scl(thrustMag);
            physics.body.applyCentralForce(thrustForce);

            // Rudder effectiveness scales with forward speed.
            // At standstill, only 10% effectiveness (barely steerable).
            // At minSpeedForFullRudder, full effectiveness.
            physics.body.getLinearVelocity(linearVel);
            float forwardSpeed = Math.abs(linearVel.dot(forward));
            float rudderEffectiveness = MathUtils.clamp(
                forwardSpeed / motor.minSpeedForFullRudder, 0.1f, 1f);

            // Apply rudder torque around world Y axis.
            // Negative sign: positive steering input = turn starboard = negative yaw.
            float yawTorque = -motor.currentRudder * motor.rudderTorque * rudderEffectiveness;
            rudderTorque.set(0f, yawTorque, 0f);
            physics.body.applyTorque(rudderTorque);

            physics.body.activate();
        } finally {
            Pools.free(forward);
            Pools.free(thrustForce);
            Pools.free(rudderTorque);
            Pools.free(linearVel);
        }
    }

    /** Returns true if any hull sample point is submerged. */
    private boolean hasSubmergedPoints(HullComponent hull) {
        for (int i = 0; i < hull.samplePoints.size; i++) {
            if (hull.samplePoints.get(i).submerged) return true;
        }
        return false;
    }

    /**
     * Exponential approach: moves {@code current} toward {@code target} at the
     * given rate per second. Produces smooth, lag-free interpolation that decays
     * naturally regardless of frame rate.
     */
    private static float expApproach(float current, float target, float rate, float dt) {
        float t = 1f - (float) Math.exp(-rate * dt);
        return current + (target - current) * t;
    }
}
