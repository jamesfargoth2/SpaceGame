package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.GravitySystem;
import com.galacticodyssey.planet.terrain.SurfaceEvents.LowGravLiftoffRiskEvent;
import com.galacticodyssey.planet.terrain.SurfaceEvents.VehicleSurfaceHeatEvent;
import com.galacticodyssey.planet.terrain.SurfaceEvents.WheelSlipEvent;

public class SurfaceVehicleSystem extends IteratingSystem {

    private static final float MAX_SINKAGE = 0.5f;
    private static final float SINKAGE_ROLLING_RESISTANCE_COEFF = 0.05f;
    private static final float STEER_TORQUE_SCALE = 0.04f;

    private final ComponentMapper<GroundVehicleComponent> vehicleMapper =
        ComponentMapper.getFor(GroundVehicleComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final GravitySystem gravitySystem;
    private final EventBus eventBus;

    private final Vector3 tmpForward = new Vector3();
    private final Vector3 tmpVel = new Vector3();
    private final Vector3 tmpUp = new Vector3();
    private final Matrix4 tmpMat = new Matrix4();
    private final Quaternion tmpQuat = new Quaternion();

    public SurfaceVehicleSystem(GravitySystem gravitySystem, EventBus eventBus) {
        super(Family.all(
            GroundVehicleComponent.class,
            PhysicsBodyComponent.class,
            TransformComponent.class).get(), 5);
        this.gravitySystem = gravitySystem;
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final GroundVehicleComponent vehicle = vehicleMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        final TransformComponent transform = transformMapper.get(entity);

        if (physics.body == null || physics.mass <= 0f) return;

        final SurfaceProperties surface = SurfaceProperties.forMaterial(vehicle.currentSurface);

        final float gravMag = gravitySystem != null
            ? gravitySystem.computeNetAcceleration(transform.position, vehicle.mass).len()
            : 9.81f;

        processTraction(entity, vehicle, physics, transform, surface, gravMag, deltaTime);
        processRegolithSinkage(vehicle, physics, surface, gravMag, deltaTime);
        checkLowGravContact(entity, vehicle, physics, gravMag);
        checkThermal(entity, vehicle, surface);
    }

    private void processTraction(Entity entity, GroundVehicleComponent vehicle,
                                  PhysicsBodyComponent physics, TransformComponent transform,
                                  SurfaceProperties surface, float gravMag, float deltaTime) {
        final float normalForce = vehicle.mass * gravMag;
        final float maxTraction = normalForce * surface.kineticFriction;

        final float requestedForce = vehicle.maxDriveForce * vehicle.throttleInput;
        final float requestedMag = Math.abs(requestedForce);

        if (requestedMag <= 0.001f) {
            vehicle.slipFraction = 0f;
        } else {
            final float clampedMag = Math.min(requestedMag, maxTraction);
            vehicle.slipFraction = MathUtils.clamp(1f - clampedMag / requestedMag, 0f, 1f);
            if (vehicle.slipFraction > 0.1f) {
                eventBus.publish(new WheelSlipEvent(entity, vehicle.slipFraction));
            }
            final float actualForce = Math.signum(requestedForce) * clampedMag;
            physics.body.getWorldTransform(tmpMat);
            tmpMat.getRotation(tmpQuat);
            tmpForward.set(0f, 0f, -1f).mul(tmpQuat).nor();
            physics.body.applyCentralForce(tmpForward.scl(actualForce));
            physics.body.activate();
        }

        applySteering(vehicle, physics);
    }

    private void applySteering(GroundVehicleComponent vehicle, PhysicsBodyComponent physics) {
        if (Math.abs(vehicle.steerInput) <= 0.001f) return;
        physics.body.getWorldTransform(tmpMat);
        tmpMat.getRotation(tmpQuat);
        tmpUp.set(0f, 1f, 0f).mul(tmpQuat).nor();
        final float torque = vehicle.steerInput * vehicle.maxSteerAngle * vehicle.mass * STEER_TORQUE_SCALE;
        physics.body.applyTorque(tmpUp.scl(torque));
        physics.body.activate();
    }

    private void processRegolithSinkage(GroundVehicleComponent vehicle,
                                         PhysicsBodyComponent physics,
                                         SurfaceProperties surface, float gravMag,
                                         float deltaTime) {
        final float contactArea = vehicle.wheelbase * vehicle.trackWidth;
        final float normalForce = vehicle.mass * gravMag;
        final float contactPressure = normalForce / Math.max(contactArea, 0.0001f);
        final float sinkage = (contactPressure / Math.max(surface.compressiveStrength, 0.001f))
            * surface.deformationDepth;
        vehicle.sinkageDepth = sinkage;

        if (sinkage > 0f) {
            final float ratio = sinkage / MAX_SINKAGE;
            final float dragFactor = 1f + ratio * ratio * 3f;
            tmpVel.set(physics.body.getLinearVelocity());
            // Project velocity onto forward axis to get scalar, then apply drag backward
            physics.body.getWorldTransform(tmpMat);
            tmpMat.getRotation(tmpQuat);
            tmpForward.set(0f, 0f, -1f).mul(tmpQuat).nor();
            final float forwardSpeed = tmpVel.dot(tmpForward);
            final float dragMag = forwardSpeed * dragFactor * normalForce * SINKAGE_ROLLING_RESISTANCE_COEFF;
            physics.body.applyCentralForce(tmpForward.scl(-dragMag));
            physics.body.activate();
        }
    }

    private void checkLowGravContact(Entity entity, GroundVehicleComponent vehicle,
                                      PhysicsBodyComponent physics, float gravMag) {
        tmpVel.set(physics.body.getLinearVelocity());
        final float speed = tmpVel.len();
        final float turnRadius = Math.max(vehicle.wheelbase * 2f, 1f);
        final float centripetal = speed * speed / turnRadius;
        final boolean canContact = vehicle.mass * gravMag > centripetal + vehicle.dynamicLift;
        if (!canContact && vehicle.anchor != null && !vehicle.anchor.isDeployed) {
            eventBus.publish(new LowGravLiftoffRiskEvent(entity));
        }
    }

    private void checkThermal(Entity entity, GroundVehicleComponent vehicle,
                               SurfaceProperties surface) {
        if (surface.temperature > 500f) {
            final float contactArea = vehicle.wheelbase * vehicle.trackWidth;
            final float heatRate = surface.thermalConductivity * contactArea
                * (surface.temperature - 300f) / 0.05f;
            eventBus.publish(new VehicleSurfaceHeatEvent(entity, heatRate));
        }
    }
}
