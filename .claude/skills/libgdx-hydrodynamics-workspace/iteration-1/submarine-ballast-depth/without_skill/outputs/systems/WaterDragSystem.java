package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.components.BuoyancyComponent;
import com.galacticodyssey.water.components.WaterDragComponent;

/**
 * Applies hydrodynamic drag forces to submerged entities.
 *
 * Drag is computed independently along the forward, lateral, and vertical axes
 * of the entity (body-local coordinates). The drag equation is:
 *   F_drag = -0.5 * waterDensity * |v|^2 * Cd * A * direction
 *
 * Angular drag is also applied to resist rotation in the water.
 *
 * Only applies drag proportional to the submerged fraction.
 */
public class WaterDragSystem extends IteratingSystem {

    private static final int PRIORITY = 8;

    private final ComponentMapper<WaterDragComponent> dragMapper =
        ComponentMapper.getFor(WaterDragComponent.class);
    private final ComponentMapper<BuoyancyComponent> buoyancyMapper =
        ComponentMapper.getFor(BuoyancyComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private static final Pool<Vector3> vectorPool = new Pool<Vector3>(4, 16) {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    private final Matrix4 tempTransform = new Matrix4();
    private final Quaternion tempQuat = new Quaternion();

    public WaterDragSystem() {
        super(Family.all(
            WaterDragComponent.class,
            BuoyancyComponent.class,
            PhysicsBodyComponent.class
        ).get(), PRIORITY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final WaterDragComponent drag = dragMapper.get(entity);
        final BuoyancyComponent buoyancy = buoyancyMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);

        if (physics.body == null || buoyancy.submergedFraction <= 0f) return;

        float subFrac = buoyancy.submergedFraction;
        float density = buoyancy.waterDensity;

        // Get the body's velocity in world space
        Vector3 worldVel = vectorPool.obtain().set(physics.body.getLinearVelocity());

        // Get the body's orientation to transform velocity into local space
        physics.body.getWorldTransform(tempTransform);
        tempTransform.getRotation(tempQuat);

        // Transform velocity to body-local frame
        Vector3 localVel = vectorPool.obtain();
        localVel.set(worldVel);
        // Inverse rotate: body-local = inverse(rotation) * world
        Quaternion invQuat = new Quaternion(tempQuat).conjugate();
        localVel.mul(invQuat);

        // Compute drag force for each axis in local space
        // F = -0.5 * rho * |v| * v * Cd * A * submergedFraction
        Vector3 localDrag = vectorPool.obtain();

        float fwdSpeed = localVel.z;
        float latSpeed = localVel.x;
        float vertSpeed = localVel.y;

        localDrag.z = -0.5f * density * Math.abs(fwdSpeed) * fwdSpeed
            * drag.forwardDragCoefficient * drag.forwardReferenceArea * subFrac;

        localDrag.x = -0.5f * density * Math.abs(latSpeed) * latSpeed
            * drag.lateralDragCoefficient * drag.lateralReferenceArea * subFrac;

        localDrag.y = -0.5f * density * Math.abs(vertSpeed) * vertSpeed
            * drag.verticalDragCoefficient * drag.verticalReferenceArea * subFrac;

        // Transform drag force back to world space
        Vector3 worldDrag = vectorPool.obtain();
        worldDrag.set(localDrag);
        worldDrag.mul(tempQuat);

        physics.body.applyCentralForce(worldDrag);

        // Angular drag: oppose rotation
        Vector3 angVel = vectorPool.obtain().set(physics.body.getAngularVelocity());

        Vector3 angDrag = vectorPool.obtain();
        angDrag.set(angVel).scl(-drag.angularDragCoefficient * subFrac);
        physics.body.applyTorque(angDrag);

        // Clean up pooled vectors
        vectorPool.free(worldVel);
        vectorPool.free(localVel);
        vectorPool.free(localDrag);
        vectorPool.free(worldDrag);
        vectorPool.free(angVel);
        vectorPool.free(angDrag);
    }
}
