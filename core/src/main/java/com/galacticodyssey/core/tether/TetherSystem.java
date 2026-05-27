package com.galacticodyssey.core.tether;

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
import com.galacticodyssey.core.tether.events.TetherBreakEvent;
import com.galacticodyssey.core.tether.events.TetherTautEvent;

/**
 * Resolves single-segment tether constraints each tick.
 * Supports elastic (Hooke's law + damping) and inelastic (position-constraint impulse) cables.
 * Applies forces and torques to both anchor entities via their Bullet rigid bodies.
 */
public class TetherSystem extends EntitySystem {

    public static final int PRIORITY = 5;

    private static final ComponentMapper<TetherConstraintComponent> TETHER_M =
        ComponentMapper.getFor(TetherConstraintComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private static final Family TETHER_FAMILY =
        Family.all(TetherConstraintComponent.class).get();

    private final EventBus eventBus;
    private ImmutableArray<Entity> tetherEntities;

    public TetherSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        tetherEntities = engine.getEntitiesFor(TETHER_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        tetherEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (tetherEntities == null) return;

        for (int i = 0, n = tetherEntities.size(); i < n; i++) {
            Entity entity = tetherEntities.get(i);
            TetherConstraintComponent tether = TETHER_M.get(entity);
            resolve(entity, tether, deltaTime);
        }
    }

    private void resolve(Entity entity, TetherConstraintComponent t, float dt) {
        if (t.isBroken) return;
        if (t.anchorA == null || t.anchorB == null) return;

        TransformComponent transformA = TRANSFORM_M.get(t.anchorA);
        TransformComponent transformB = TRANSFORM_M.get(t.anchorB);
        if (transformA == null || transformB == null) return;

        PhysicsBodyComponent physA = PHYSICS_M.get(t.anchorA);
        PhysicsBodyComponent physB = PHYSICS_M.get(t.anchorB);
        if (physA == null || physB == null) return;
        if (physA.body == null || physB.body == null) return;

        // Compute world-space attachment points
        Vector3 worldOffsetA = Pools.obtain(Vector3.class);
        Vector3 worldOffsetB = Pools.obtain(Vector3.class);
        Vector3 posA = Pools.obtain(Vector3.class);
        Vector3 posB = Pools.obtain(Vector3.class);
        Vector3 delta = Pools.obtain(Vector3.class);
        Vector3 dir = Pools.obtain(Vector3.class);
        Vector3 force = Pools.obtain(Vector3.class);
        Vector3 torque = Pools.obtain(Vector3.class);
        Vector3 velA = Pools.obtain(Vector3.class);
        Vector3 velB = Pools.obtain(Vector3.class);
        Vector3 relVel = Pools.obtain(Vector3.class);

        try {
            // Transform local offsets to world space using entity rotation
            rotateByQuaternion(worldOffsetA.set(t.localOffsetA), transformA.rotation);
            rotateByQuaternion(worldOffsetB.set(t.localOffsetB), transformB.rotation);

            posA.set(transformA.position).add(worldOffsetA);
            posB.set(transformB.position).add(worldOffsetB);

            delta.set(posB).sub(posA);
            float dist = delta.len();
            t.currentLength = dist;

            // Avoid division by zero
            if (dist < 1e-6f) {
                t.isSlack = true;
                t.currentTension = 0f;
                return;
            }

            dir.set(delta).scl(1f / dist); // normalize

            boolean wasSlack = t.isSlack;

            // Slack check for inelastic cables
            if (dist <= t.maxLength && t.springConstant == 0f) {
                t.isSlack = true;
                t.currentTension = 0f;
                return;
            }

            t.isSlack = false;

            // Publish taut event on transition from slack to taut
            if (wasSlack) {
                eventBus.publish(new TetherTautEvent(entity));
            }

            float tensionMag;

            if (t.springConstant > 0f) {
                // Elastic: Hooke's law + velocity damping
                float extension = dist - t.restLength;
                tensionMag = t.springConstant * extension;

                // Velocity damping along tether axis
                getLinearVelocity(physA, velA);
                getLinearVelocity(physB, velB);
                relVel.set(velB).sub(velA);
                float relVelAlongTether = relVel.dot(dir);
                tensionMag += t.damping * relVelAlongTether;

                // Cable cannot push (no compression)
                tensionMag = Math.max(0f, tensionMag);
            } else {
                // Inelastic: position-constraint impulse
                tensionMag = resolveInelasticTension(physA, physB, dir, dist, t.restLength, dt);
            }

            t.currentTension = tensionMag;

            // Check break threshold
            if (tensionMag >= t.breakTension) {
                t.isBroken = true;
                t.currentTension = tensionMag;
                eventBus.publish(new TetherBreakEvent(entity, tensionMag));
                return;
            }

            // Apply equal and opposite forces at attachment points
            force.set(dir).scl(tensionMag);

            // Force on A: pulls toward B (along +dir)
            applyForceAtOffset(physA, force, worldOffsetA, torque);

            // Force on B: pulls toward A (along -dir)
            force.scl(-1f);
            applyForceAtOffset(physB, force, worldOffsetB, torque);

        } finally {
            Pools.free(worldOffsetA);
            Pools.free(worldOffsetB);
            Pools.free(posA);
            Pools.free(posB);
            Pools.free(delta);
            Pools.free(dir);
            Pools.free(force);
            Pools.free(torque);
            Pools.free(velA);
            Pools.free(velB);
            Pools.free(relVel);
        }
    }

    /**
     * Inelastic constraint: computes the impulse magnitude needed to prevent
     * the two bodies from separating beyond restLength.
     */
    private float resolveInelasticTension(PhysicsBodyComponent physA,
                                          PhysicsBodyComponent physB,
                                          Vector3 dir, float dist,
                                          float restLength, float dt) {
        if (dist <= restLength) return 0f;

        float invMassA = (physA.mass > 0f) ? 1f / physA.mass : 0f;
        float invMassB = (physB.mass > 0f) ? 1f / physB.mass : 0f;
        float invMassSum = invMassA + invMassB;
        if (invMassSum <= 0f) return 0f;

        Vector3 velA = Pools.obtain(Vector3.class);
        Vector3 velB = Pools.obtain(Vector3.class);
        Vector3 relVel = Pools.obtain(Vector3.class);

        try {
            getLinearVelocity(physA, velA);
            getLinearVelocity(physB, velB);
            relVel.set(velB).sub(velA);
            float vDotDir = relVel.dot(dir);

            // Only constrain if bodies are moving apart (positive = separating along A->B)
            if (vDotDir >= 0f) return 0f;

            // Impulse needed to zero out the separating velocity, converted to force via dt
            float j = -vDotDir / invMassSum;
            return Math.max(0f, j / dt);
        } finally {
            Pools.free(velA);
            Pools.free(velB);
            Pools.free(relVel);
        }
    }

    /**
     * Applies a force at an offset from the body's centre of mass,
     * producing both a linear force and a torque (r x F).
     */
    private void applyForceAtOffset(PhysicsBodyComponent phys, Vector3 worldForce,
                                    Vector3 worldOffset, Vector3 torqueOut) {
        phys.body.applyCentralForce(worldForce);

        // Torque = worldOffset x worldForce
        torqueOut.set(worldOffset).crs(worldForce);
        phys.body.applyTorque(torqueOut);
    }

    /** Reads the linear velocity from a Bullet rigid body into the output vector. */
    private void getLinearVelocity(PhysicsBodyComponent phys, Vector3 out) {
        out.set(phys.body.getLinearVelocity());
    }

    /** Rotates a vector by a quaternion in-place. */
    private void rotateByQuaternion(Vector3 v, Quaternion q) {
        // v' = q * v * q^-1, using libGDX's built-in rotation
        float x = v.x, y = v.y, z = v.z;
        float qx = q.x, qy = q.y, qz = q.z, qw = q.w;

        // t = 2 * cross(q.xyz, v)
        float tx = 2f * (qy * z - qz * y);
        float ty = 2f * (qz * x - qx * z);
        float tz = 2f * (qx * y - qy * x);

        // v' = v + w * t + cross(q.xyz, t)
        v.x = x + qw * tx + (qy * tz - qz * ty);
        v.y = y + qw * ty + (qz * tx - qx * tz);
        v.z = z + qw * tz + (qx * ty - qy * tx);
    }
}
