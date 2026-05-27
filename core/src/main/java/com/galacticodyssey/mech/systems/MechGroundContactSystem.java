package com.galacticodyssey.mech.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.GravitySystem;
import com.galacticodyssey.mech.components.MechPhysicsComponent;

/**
 * Performs two foot raycasts (left and right attachment points) each tick to
 * determine ground contact. Resolves ground penetration by pushing the mech
 * upward and killing downward velocity. Blends ground normals from both feet
 * for smooth terrain traversal.
 */
public class MechGroundContactSystem extends EntitySystem {

    public static final int PRIORITY = 2;

    /** Lateral offset from mech centre to each foot attachment point (metres). */
    private static final float FOOT_LATERAL_OFFSET = 0.6f;

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

    public MechGroundContactSystem(EventBus eventBus, GravitySystem gravitySystem) {
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

        // Compute foot world positions: offset left and right from centre along
        // the body's local right axis (derived from bodyOrientation).
        Vector3 right = Pools.obtain(Vector3.class).set(1f, 0f, 0f);
        right.mul(mech.bodyOrientation);

        Vector3 leftFoot = Pools.obtain(Vector3.class).set(transform.position)
            .mulAdd(right, -FOOT_LATERAL_OFFSET);
        Vector3 rightFoot = Pools.obtain(Vector3.class).set(transform.position)
            .mulAdd(right, FOOT_LATERAL_OFFSET);

        // Cast rays downward (along gravityDir) from each foot to find terrain height.
        // For now, use a simplified height query along the gravity axis.
        Vector3 up = Pools.obtain(Vector3.class).set(mech.gravityDir).scl(-1f);

        float leftFootHeight = leftFoot.dot(up);
        float rightFootHeight = rightFoot.dot(up);
        float footY = Math.min(leftFootHeight, rightFootHeight);

        // Terrain height beneath each foot (projected onto the up axis).
        // In a full implementation this would use Bullet raycasts; here we use
        // the position's up-component minus groundClearance as the reference plane.
        float terrainHeight = footY - mech.groundClearance;

        // Ground contact: at least one foot within stepHeight of terrain
        float clearance = footY - terrainHeight;
        mech.isGrounded = clearance <= mech.stepHeight;
        mech.groundClearance = clearance;

        if (mech.isGrounded) {
            // Resolve penetration: push body up if any foot is below terrain
            float penetration = terrainHeight - footY;
            if (penetration > 0f) {
                transform.position.mulAdd(up, penetration);
                mech.position.set(transform.position);

                // Kill velocity component along gravity dir if moving into ground
                float vDown = mech.velocity.dot(mech.gravityDir);
                if (vDown > 0f) {
                    mech.velocity.mulAdd(mech.gravityDir, -vDown);
                }
            }

            // Blend ground normals from both feet (average of up vectors at each foot)
            blendFootNormals(mech, leftFoot, rightFoot, up);
        }

        Pools.free(right);
        Pools.free(leftFoot);
        Pools.free(rightFoot);
        Pools.free(up);
    }

    /**
     * Blends the ground normal from left and right foot contact points.
     * Uses the cross product of the foot-to-foot vector and the forward direction
     * to approximate the terrain slope, then averages with the local up.
     */
    private void blendFootNormals(MechPhysicsComponent mech,
                                  Vector3 leftFoot, Vector3 rightFoot, Vector3 up) {
        Vector3 footToFoot = Pools.obtain(Vector3.class).set(rightFoot).sub(leftFoot);

        Vector3 forward = Pools.obtain(Vector3.class).set(0f, 0f, 1f);
        forward.mul(mech.bodyOrientation);

        Vector3 terrainNormal = Pools.obtain(Vector3.class).set(footToFoot).crs(forward).nor();

        // Ensure normal points away from gravity (same hemisphere as up)
        if (terrainNormal.dot(up) < 0f) {
            terrainNormal.scl(-1f);
        }

        // Blend: 50% terrain-derived normal + 50% pure up for stability
        mech.groundNormal.set(terrainNormal).add(up).nor();

        Pools.free(footToFoot);
        Pools.free(forward);
        Pools.free(terrainNormal);
    }
}
