package com.galacticodyssey.core.tether;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.GravitySystem;
import com.galacticodyssey.core.tether.VerletRopeComponent.RopeSegment;

/**
 * Simulates multi-segment Verlet ropes with gravity and distance constraints.
 * Each tick: Verlet integration for non-fixed segments, then iterative constraint relaxation.
 * Uses GravitySystem to compute gravity at each segment's position rather than a hardcoded vector.
 */
public class VerletRopeSystem extends EntitySystem {

    public static final int PRIORITY = 6;

    private static final ComponentMapper<VerletRopeComponent> ROPE_M =
        ComponentMapper.getFor(VerletRopeComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final Family ROPE_FAMILY =
        Family.all(VerletRopeComponent.class).get();

    private final EventBus eventBus;
    private final GravitySystem gravitySystem;
    private ImmutableArray<Entity> ropeEntities;

    /**
     * @param eventBus      central event bus
     * @param gravitySystem gravity system used to compute per-segment gravity vectors
     */
    public VerletRopeSystem(EventBus eventBus, GravitySystem gravitySystem) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.gravitySystem = gravitySystem;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ropeEntities = engine.getEntitiesFor(ROPE_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        ropeEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (ropeEntities == null) return;

        for (int i = 0, n = ropeEntities.size(); i < n; i++) {
            Entity entity = ropeEntities.get(i);
            VerletRopeComponent rope = ROPE_M.get(entity);
            simulate(rope, deltaTime);
        }
    }

    private void simulate(VerletRopeComponent rope, float dt) {
        Array<RopeSegment> segments = rope.segments;
        if (segments.size < 2) return;

        float dtSq = dt * dt;

        // --- Verlet integration ---
        Vector3 velocity = Pools.obtain(Vector3.class);
        Vector3 gravity = Pools.obtain(Vector3.class);

        try {
            for (int i = 0, n = segments.size; i < n; i++) {
                RopeSegment seg = segments.get(i);
                if (seg.isFixed) continue;

                // Implicit velocity from position difference
                velocity.set(seg.position).sub(seg.prevPosition);

                seg.prevPosition.set(seg.position);

                // Inertia: position += velocity
                seg.position.add(velocity);

                // Gravity at this segment's position (uses GravitySystem for per-point accuracy)
                gravity.setZero();
                if (gravitySystem != null) {
                    Vector3 accel = gravitySystem.computeNetAcceleration(seg.position, seg.mass);
                    gravity.set(accel);
                }

                // position += gravity * dt^2 (Verlet gravity term)
                seg.position.mulAdd(gravity, dtSq);
            }
        } finally {
            Pools.free(velocity);
            Pools.free(gravity);
        }

        // --- Constraint relaxation ---
        Vector3 delta = Pools.obtain(Vector3.class);
        Vector3 correction = Pools.obtain(Vector3.class);

        try {
            for (int iter = 0; iter < rope.solverIterations; iter++) {
                for (int i = 0, n = segments.size - 1; i < n; i++) {
                    RopeSegment a = segments.get(i);
                    RopeSegment b = segments.get(i + 1);

                    delta.set(b.position).sub(a.position);
                    float dist = delta.len();
                    if (dist < 1e-8f) continue;

                    float error = (dist - rope.segmentLength) / dist * rope.stiffness;

                    correction.set(delta).scl(0.5f * error);

                    if (!a.isFixed) {
                        a.position.add(correction);
                    }
                    if (!b.isFixed) {
                        b.position.sub(correction);
                    }
                }
            }
        } finally {
            Pools.free(delta);
            Pools.free(correction);
        }
    }
}
