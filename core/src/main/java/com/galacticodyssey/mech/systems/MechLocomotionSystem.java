package com.galacticodyssey.mech.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mech.components.MechInputComponent;
import com.galacticodyssey.mech.components.MechPhysicsComponent;
import com.galacticodyssey.mech.components.MechPhysicsComponent.LocomotionMode;

/**
 * Converts mech input into velocity changes each tick. Walk speed scales
 * with sqrt(gravity/9.81) so low-gravity environments feel appropriately
 * floaty. Applies momentum-based yaw turning with damping. In JETPACK mode,
 * applies 6DOF thrust along all body axes.
 */
public class MechLocomotionSystem extends EntitySystem {

    public static final int PRIORITY = 4;

    private static final float STANDARD_GRAVITY = 9.81f;
    private static final float MIN_INPUT_DEADZONE = 0.1f;

    private static final Family FAMILY = Family.all(
        MechPhysicsComponent.class, MechInputComponent.class
    ).get();

    private static final ComponentMapper<MechPhysicsComponent> MECH_M =
        ComponentMapper.getFor(MechPhysicsComponent.class);
    private static final ComponentMapper<MechInputComponent> INPUT_M =
        ComponentMapper.getFor(MechInputComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public MechLocomotionSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (entities == null) return;

        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity entity = entities.get(i);
            MechPhysicsComponent mech = MECH_M.get(entity);
            MechInputComponent input = INPUT_M.get(entity);

            if (mech.locomotionMode == LocomotionMode.JETPACK) {
                applyJetpackThrust(mech, input, deltaTime);
            } else {
                applyWalkingMovement(mech, input, deltaTime);
            }
            applyYawTurn(mech, input.turnInput, deltaTime);
        }
    }

    private void applyWalkingMovement(MechPhysicsComponent mech, MechInputComponent input,
                                      float deltaTime) {
        // Scale max speed with gravity: sqrt(grav/9.81) so low-g is slower, high-g faster
        float gravMag = mech.gravityDir.len() * STANDARD_GRAVITY; // gravityDir is normalized
        // Use the actual gravity magnitude from gravityDir (which is a unit vector,
        // so we need the original magnitude). We approximate from the gravityDir norm.
        // Since gravityDir is always normalized by MechOrientationSystem, we use
        // STANDARD_GRAVITY as the reference and scale from the environment.
        float gravFactor = MathUtils.clamp(gravMag / STANDARD_GRAVITY, 0.1f, 3f);
        float effectiveMaxSpeed = mech.maxWalkSpeed * (float) Math.sqrt(gravFactor);

        // Build desired velocity in body-local space
        Vector3 desiredLocalVel = Pools.obtain(Vector3.class).set(
            input.moveInput.x * effectiveMaxSpeed,
            0f,
            input.moveInput.y * effectiveMaxSpeed
        );

        // Rotate to world space via body orientation
        Vector3 desiredWorldVel = Pools.obtain(Vector3.class).set(desiredLocalVel);
        desiredWorldVel.mul(mech.bodyOrientation);
        Pools.free(desiredLocalVel);

        // Project current velocity onto the plane perpendicular to gravityDir
        Vector3 currentHorizVel = projectOntoPlane(mech.velocity, mech.gravityDir);

        // Compute delta and clamp by acceleration/deceleration
        Vector3 velDelta = Pools.obtain(Vector3.class).set(desiredWorldVel).sub(currentHorizVel);

        boolean hasInput = input.moveInput.len() > MIN_INPUT_DEADZONE;
        float maxDelta = (hasInput ? mech.acceleration : mech.deceleration) * deltaTime;
        if (velDelta.len() > maxDelta) {
            velDelta.nor().scl(maxDelta);
        }

        mech.velocity.add(velDelta);

        Pools.free(desiredWorldVel);
        Pools.free(currentHorizVel);
        Pools.free(velDelta);
    }

    private void applyJetpackThrust(MechPhysicsComponent mech, MechInputComponent input,
                                    float deltaTime) {
        // In jetpack mode, moveInput drives 6DOF translation through body axes
        Vector3 thrustLocal = Pools.obtain(Vector3.class).set(
            input.moveInput.x * mech.acceleration,
            0f,
            input.moveInput.y * mech.acceleration
        );

        Vector3 thrustWorld = Pools.obtain(Vector3.class).set(thrustLocal);
        thrustWorld.mul(mech.bodyOrientation);
        Pools.free(thrustLocal);

        mech.velocity.mulAdd(thrustWorld, deltaTime);

        // Clamp to max speed
        float maxSpeed = mech.maxWalkSpeed * 1.5f; // jetpack allows slightly higher speed
        if (mech.velocity.len2() > maxSpeed * maxSpeed) {
            mech.velocity.nor().scl(maxSpeed);
        }

        Pools.free(thrustWorld);
    }

    private void applyYawTurn(MechPhysicsComponent mech, float turnInput, float deltaTime) {
        float desiredYawVel = turnInput * mech.maxYawRate;

        // Inertia: lighter mechs turn more nimbly
        float maxYawAccel = mech.maxYawRate / (mech.mass / 1000f);
        float yawDelta = (desiredYawVel - mech.yawVelocity) * mech.yawDamping * deltaTime;
        yawDelta = MathUtils.clamp(yawDelta, -maxYawAccel * deltaTime, maxYawAccel * deltaTime);

        mech.yawVelocity += yawDelta;

        // Natural decay (momentum damping)
        mech.yawVelocity *= (1f - mech.yawDamping * deltaTime);

        mech.yaw += mech.yawVelocity * deltaTime;
    }

    /**
     * Projects a vector onto the plane perpendicular to the given normal.
     * Caller must free the returned vector via {@code Pools.free()}.
     */
    private static Vector3 projectOntoPlane(Vector3 v, Vector3 normal) {
        Vector3 result = Pools.obtain(Vector3.class).set(v);
        float dot = v.dot(normal);
        Vector3 projection = Pools.obtain(Vector3.class).set(normal).scl(dot);
        result.sub(projection);
        Pools.free(projection);
        return result;
    }
}
