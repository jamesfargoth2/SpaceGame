package com.galacticodyssey.ship.docking;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Enforces the structural constraint between two hard-docked ships so they
 * move as a single rigid assembly.
 * <p>
 * Uses Baumgarte stabilisation to correct position drift at the connection
 * point and zeroes relative velocity at the constraint, distributing
 * corrections proportional to each ship's inverse mass.
 */
public class HardDockConstraintSystem extends EntitySystem {

    public static final int PRIORITY = 8;

    /** Baumgarte stabilisation factor -- fraction of position error corrected per frame. */
    private static final float BAUMGARTE_FACTOR = 0.5f;

    private static final Family FAMILY = Family.all(
        HardDockConstraintComponent.class
    ).get();

    private static final ComponentMapper<HardDockConstraintComponent> CONSTRAINT_M =
        ComponentMapper.getFor(HardDockConstraintComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    public HardDockConstraintSystem(EventBus eventBus) {
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
        if (entities == null || deltaTime <= 0f) return;

        for (int i = 0, n = entities.size(); i < n; i++) {
            processConstraint(entities.get(i), deltaTime);
        }
    }

    private void processConstraint(Entity constraintEntity, float dt) {
        HardDockConstraintComponent constraint = CONSTRAINT_M.get(constraintEntity);
        if (!constraint.isActive) return;

        Entity shipAEntity = constraint.shipA;
        Entity shipBEntity = constraint.shipB;
        if (shipAEntity == null || shipBEntity == null) return;

        TransformComponent transformA = TRANSFORM_M.get(shipAEntity);
        TransformComponent transformB = TRANSFORM_M.get(shipBEntity);
        PhysicsBodyComponent physicsA = PHYSICS_M.get(shipAEntity);
        PhysicsBodyComponent physicsB = PHYSICS_M.get(shipBEntity);

        if (transformA == null || transformB == null) return;
        if (physicsA == null || physicsB == null) return;
        if (physicsA.body == null || physicsB.body == null) return;

        float invMassA = (physicsA.mass > 0f) ? 1f / physicsA.mass : 0f;
        float invMassB = (physicsB.mass > 0f) ? 1f / physicsB.mass : 0f;
        float invMassSum = invMassA + invMassB;
        if (invMassSum <= 0f) return;

        Vector3 worldA = vectorPool.obtain();
        Vector3 worldB = vectorPool.obtain();
        Vector3 error = vectorPool.obtain();
        Vector3 velA = vectorPool.obtain();
        Vector3 velB = vectorPool.obtain();
        Vector3 relVel = vectorPool.obtain();
        Vector3 correction = vectorPool.obtain();

        try {
            // Compute world-space positions of the attachment points
            worldA.set(constraint.localOffsetA).mul(transformA.rotation).add(transformA.position);
            worldB.set(constraint.localOffsetB).mul(transformB.rotation).add(transformB.position);

            // --- Position correction (Baumgarte stabilisation) ---
            error.set(worldB).sub(worldA);
            float errorLen = error.len();

            if (errorLen > 1e-6f) {
                // Correction magnitude distributed by inverse mass
                float correctionMag = BAUMGARTE_FACTOR * errorLen / dt;
                correction.set(error).nor();

                // Move A toward B and B toward A, weighted by inverse mass
                float moveA = correctionMag * invMassA / invMassSum * dt;
                float moveB = correctionMag * invMassB / invMassSum * dt;

                transformA.position.mulAdd(correction, moveA);
                transformB.position.mulAdd(correction, -moveB);
            }

            // --- Velocity correction: zero relative velocity at constraint point ---
            velA.set(physicsA.body.getLinearVelocity());
            velB.set(physicsB.body.getLinearVelocity());
            relVel.set(velB).sub(velA);

            float relSpeed = relVel.len();
            if (relSpeed > 1e-6f) {
                Vector3 dir = error.set(relVel).nor();
                float jMag = relSpeed / invMassSum;

                // Impulse to ship A (in direction of relative velocity)
                correction.set(dir).scl(jMag * invMassA);
                velA.add(correction);

                // Impulse to ship B (opposite direction)
                correction.set(dir).scl(-jMag * invMassB);
                velB.add(correction);

                physicsA.body.setLinearVelocity(velA);
                physicsB.body.setLinearVelocity(velB);
            }

        } finally {
            vectorPool.free(worldA);
            vectorPool.free(worldB);
            vectorPool.free(error);
            vectorPool.free(velA);
            vectorPool.free(velB);
            vectorPool.free(relVel);
            vectorPool.free(correction);
        }
    }
}
