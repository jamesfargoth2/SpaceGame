package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.components.BallastTankComponent;
import com.galacticodyssey.water.components.BuoyancyComponent;
import com.galacticodyssey.water.components.DepthControlComponent;
import com.galacticodyssey.water.components.SubmarineHullComponent;
import com.galacticodyssey.water.components.SubmarineStateComponent;

/**
 * Calculates and applies buoyancy forces to submarine entities.
 *
 * Buoyancy force = waterDensity * g * submergedVolume (upward)
 * Weight force = effectiveMass * g (downward)
 *
 * The submerged fraction is computed from the entity's Y position
 * relative to the water surface level, clamped to [0, 1].
 *
 * Also computes stability torques based on the metacentric height
 * (distance between center of gravity and metacenter). If flooding
 * shifts the center of gravity, the submarine will list.
 */
public class BuoyancySystem extends IteratingSystem {

    private static final int PRIORITY = 5;

    private final ComponentMapper<BuoyancyComponent> buoyancyMapper =
        ComponentMapper.getFor(BuoyancyComponent.class);
    private final ComponentMapper<SubmarineHullComponent> hullMapper =
        ComponentMapper.getFor(SubmarineHullComponent.class);
    private final ComponentMapper<BallastTankComponent> ballastMapper =
        ComponentMapper.getFor(BallastTankComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<DepthControlComponent> depthMapper =
        ComponentMapper.getFor(DepthControlComponent.class);
    private final ComponentMapper<SubmarineStateComponent> stateMapper =
        ComponentMapper.getFor(SubmarineStateComponent.class);

    private static final Pool<Vector3> vectorPool = new Pool<Vector3>(4, 16) {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    private final Matrix4 tempTransform = new Matrix4();

    public BuoyancySystem() {
        super(Family.all(
            BuoyancyComponent.class,
            SubmarineHullComponent.class,
            PhysicsBodyComponent.class
        ).get(), PRIORITY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final BuoyancyComponent buoyancy = buoyancyMapper.get(entity);
        final SubmarineHullComponent hull = hullMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        final BallastTankComponent ballast = ballastMapper.get(entity);
        final DepthControlComponent depth = depthMapper.get(entity);
        final SubmarineStateComponent state = stateMapper.get(entity);

        if (physics.body == null) return;

        // Get current position
        Vector3 position = vectorPool.obtain();
        physics.body.getWorldTransform(tempTransform);
        tempTransform.getTranslation(position);

        // Compute depth (positive = below surface)
        float currentDepth = buoyancy.surfaceLevel - position.y;

        // Update depth control component if present
        float previousDepth = 0f;
        if (depth != null) {
            previousDepth = depth.currentDepth;
            depth.currentDepth = currentDepth;
            depth.verticalSpeed = (currentDepth - previousDepth) / Math.max(deltaTime, 0.001f);
        }

        // Calculate submerged fraction based on hull height
        float halfHeight = hull.height * 0.5f;
        float topOfHull = position.y + halfHeight;
        float bottomOfHull = position.y - halfHeight;

        if (topOfHull <= buoyancy.surfaceLevel) {
            buoyancy.submergedFraction = 1f;
            buoyancy.fullySubmerged = true;
            buoyancy.atSurface = false;
        } else if (bottomOfHull >= buoyancy.surfaceLevel) {
            buoyancy.submergedFraction = 0f;
            buoyancy.fullySubmerged = false;
            buoyancy.atSurface = false;
        } else {
            float submergedHeight = buoyancy.surfaceLevel - bottomOfHull;
            buoyancy.submergedFraction = MathUtils.clamp(submergedHeight / hull.height, 0f, 1f);
            buoyancy.fullySubmerged = false;
            buoyancy.atSurface = true;
        }

        // Calculate effective mass (dry mass + ballast water + flood water)
        float ballastWaterMass = (ballast != null) ? ballast.getTotalWaterMass() : 0f;
        float floodWaterMass = (state != null) ? state.totalFloodWaterMass : 0f;
        buoyancy.effectiveMass = hull.dryMass + ballastWaterMass + floodWaterMass;

        // Buoyancy force: waterDensity * g * submergedVolume (upward)
        float submergedVolume = hull.displacementVolume * buoyancy.submergedFraction;
        float buoyancyForce = buoyancy.waterDensity * buoyancy.gravity * submergedVolume;

        // Weight force: effectiveMass * g (downward)
        float weightForce = buoyancy.effectiveMass * buoyancy.gravity;

        // Net buoyancy (positive = upward)
        buoyancy.netBuoyancyForce = buoyancyForce - weightForce;

        // Apply net vertical force to the rigid body
        Vector3 force = vectorPool.obtain();
        force.set(0f, buoyancy.netBuoyancyForce, 0f);
        physics.body.applyCentralForce(force);

        // Stability torque from metacentric height
        // If flooding creates asymmetric weight, compute restoring/capsizing torque
        if (state != null && buoyancy.submergedFraction > 0f) {
            applyStabilityTorques(entity, physics, buoyancy, state, hull);
        }

        vectorPool.free(force);
        vectorPool.free(position);
    }

    /**
     * Applies restoring torques based on metacentric height and flooding-induced
     * weight asymmetry. A positive metacentric height produces a restoring torque
     * that rights the submarine. Asymmetric flooding shifts the CG and causes listing.
     */
    private void applyStabilityTorques(Entity entity, PhysicsBodyComponent physics,
                                        BuoyancyComponent buoyancy, SubmarineStateComponent state,
                                        SubmarineHullComponent hull) {
        // Get current orientation
        Vector3 torque = vectorPool.obtain();

        // Restoring torque proportional to metacentric height and displacement
        float displacementForce = buoyancy.waterDensity * buoyancy.gravity
            * hull.displacementVolume * buoyancy.submergedFraction;

        // Roll restoring torque: -GM * displacement * sin(roll)
        // The flood-induced roll offset shifts the equilibrium angle
        float targetRollRad = state.floodInducedRoll * MathUtils.degreesToRadians;

        // Extract current roll from the transform
        Vector3 up = vectorPool.obtain();
        Vector3 right = vectorPool.obtain();
        physics.body.getWorldTransform(tempTransform);
        tempTransform.getTranslation(up); // just to reuse the temp
        up.set(0, 1, 0);
        right.set(1, 0, 0);
        // Simplified: use the body's angular velocity for damping
        Vector3 angVel = vectorPool.obtain().set(physics.body.getAngularVelocity());

        float rollTorque = -buoyancy.metacentricHeight * displacementForce * (angVel.z * 0.1f - targetRollRad);
        float pitchTorque = -buoyancy.metacentricHeight * displacementForce * (angVel.x * 0.1f
            - state.floodInducedPitch * MathUtils.degreesToRadians);

        torque.set(pitchTorque * 0.5f, 0f, rollTorque * 0.5f);
        physics.body.applyTorque(torque);

        vectorPool.free(angVel);
        vectorPool.free(right);
        vectorPool.free(up);
        vectorPool.free(torque);
    }
}
