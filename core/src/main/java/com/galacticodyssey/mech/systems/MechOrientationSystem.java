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
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.GravitySystem;
import com.galacticodyssey.mech.components.MechPhysicsComponent;
import com.galacticodyssey.mech.components.MechPhysicsComponent.LocomotionMode;
import com.galacticodyssey.mech.events.MechLocomotionModeChangeEvent;

/**
 * Reads the gravity vector from {@link GravitySystem} each tick and builds
 * the mech's body orientation via Gram-Schmidt orthonormalization of
 * gravityDir + yaw. In zero-g, freezes orientation and switches to JETPACK mode.
 */
public class MechOrientationSystem extends EntitySystem {

    public static final int PRIORITY = 3;

    private static final Family FAMILY = Family.all(
        MechPhysicsComponent.class, TransformComponent.class
    ).get();

    private static final ComponentMapper<MechPhysicsComponent> MECH_M =
        ComponentMapper.getFor(MechPhysicsComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;
    private final GravitySystem gravitySystem;
    private ImmutableArray<Entity> entities;

    public MechOrientationSystem(EventBus eventBus, GravitySystem gravitySystem) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.gravitySystem = gravitySystem;
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
            processEntity(entities.get(i));
        }
    }

    private void processEntity(Entity entity) {
        MechPhysicsComponent mech = MECH_M.get(entity);
        TransformComponent transform = TRANSFORM_M.get(entity);

        // Query gravity at the mech's current position
        Vector3 netAccel = gravitySystem.computeNetAcceleration(transform.position, mech.mass);

        if (gravitySystem.isInZeroG(transform.position)) {
            // Zero-g: keep last orientation, switch to jetpack
            if (mech.locomotionMode != LocomotionMode.JETPACK) {
                mech.locomotionMode = LocomotionMode.JETPACK;
                mech.isGrounded = false;
                eventBus.publish(new MechLocomotionModeChangeEvent(entity, LocomotionMode.JETPACK));
            }
            return;
        }

        // Ensure walking mode when gravity is present
        if (mech.locomotionMode != LocomotionMode.WALKING) {
            mech.locomotionMode = LocomotionMode.WALKING;
            eventBus.publish(new MechLocomotionModeChangeEvent(entity, LocomotionMode.WALKING));
        }

        // gravityDir = normalised net acceleration (points "down")
        mech.gravityDir.set(netAccel).nor();

        // Build orientation: up = -gravityDir, forward = yaw rotated around up,
        // then Gram-Schmidt to make the basis orthonormal.
        Vector3 up = Pools.obtain(Vector3.class).set(mech.gravityDir).scl(-1f);
        Vector3 forward = Pools.obtain(Vector3.class).set(
            MathUtils.cos(mech.yaw), 0f, MathUtils.sin(mech.yaw)
        );

        // Gram-Schmidt: remove up-component from forward
        float dot = forward.dot(up);
        Vector3 upProjection = Pools.obtain(Vector3.class).set(up).scl(dot);
        forward.sub(upProjection);
        Pools.free(upProjection);

        float forwardLen = forward.len();
        if (forwardLen > 1e-6f) {
            forward.scl(1f / forwardLen);
        } else {
            // Degenerate case: pick an arbitrary perpendicular to up
            forward.set(up.y, -up.x, 0f).nor();
        }

        Vector3 right = Pools.obtain(Vector3.class).set(forward).crs(up).nor();

        mech.bodyOrientation.setFromAxes(
            right.x,   right.y,   right.z,
            up.x,      up.y,      up.z,
            forward.x, forward.y, forward.z
        );

        Pools.free(up);
        Pools.free(forward);
        Pools.free(right);
    }
}
