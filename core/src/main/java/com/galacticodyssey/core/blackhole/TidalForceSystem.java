package com.galacticodyssey.core.blackhole;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.blackhole.events.TidalDamageEvent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Applies differential tidal forces to entities with a
 * {@link PhysicsBodyComponent} that are near a black hole.
 *
 * <p>Tidal acceleration across an extended body of length L at distance r
 * from a mass M is approximately:
 * <pre>
 *   a_tidal = 2 * G * M * L / r^3
 * </pre>
 *
 * <p>The stretching force is applied along the axis toward the black hole via
 * the entity's {@link com.badlogic.gdx.physics.bullet.dynamics.btRigidBody}.
 * When tidal acceleration exceeds {@link #TIDAL_DAMAGE_THRESHOLD}, a
 * {@link TidalDamageEvent} is published for the damage system to consume.
 *
 * <p>Priority 2 (runs after EventHorizonSystem, before TimeDilationSystem).
 */
public class TidalForceSystem extends EntitySystem {

    public static final int PRIORITY = 2;

    /**
     * Default tidal acceleration threshold (m/s^2) above which structural
     * damage occurs. Ships with stronger hulls can override this per-entity
     * in future iterations.
     */
    public static final float TIDAL_DAMAGE_THRESHOLD = 50f;

    /**
     * Default approximate body length used when no per-entity length is
     * available. Represents a mid-size ship (~20 game-units long).
     */
    private static final float DEFAULT_BODY_LENGTH = 20f;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BlackHoleComponent> BLACK_HOLE_M =
        ComponentMapper.getFor(BlackHoleComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private static final Family BLACK_HOLE_FAMILY = Family.all(
        TransformComponent.class, BlackHoleComponent.class
    ).get();

    private static final Family BODY_FAMILY = Family.all(
        TransformComponent.class, PhysicsBodyComponent.class
    ).get();

    private final EventBus eventBus;

    private ImmutableArray<Entity> blackHoles;
    private ImmutableArray<Entity> bodies;

    public TidalForceSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        blackHoles = engine.getEntitiesFor(BLACK_HOLE_FAMILY);
        bodies = engine.getEntitiesFor(BODY_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        blackHoles = null;
        bodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (blackHoles.size() == 0) return;

        Vector3 toBH = Pools.obtain(Vector3.class);
        Vector3 stretchForce = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < bodies.size(); i++) {
                Entity body = bodies.get(i);

                // Black holes don't tidally stress themselves
                if (BLACK_HOLE_M.has(body)) continue;

                TransformComponent bodyTransform = TRANSFORM_M.get(body);
                PhysicsBodyComponent physics = PHYSICS_M.get(body);

                for (int j = 0; j < blackHoles.size(); j++) {
                    Entity bhEntity = blackHoles.get(j);
                    BlackHoleComponent bh = BLACK_HOLE_M.get(bhEntity);
                    TransformComponent bhTransform = TRANSFORM_M.get(bhEntity);

                    toBH.set(bhTransform.position).sub(bodyTransform.position);
                    float dist = toBH.len();

                    // Skip if outside influence range (accretion disk outer is a
                    // reasonable cutoff for tidal relevance)
                    if (dist > bh.accretionDiskOuter) continue;

                    // Clamp to schwarzschild radius to prevent r^3 explosion
                    float r = Math.max(dist, bh.schwarzschildRadius);

                    float tidalAcc = computeTidalAcceleration(
                        BlackHoleComponent.G * bh.mass, r, DEFAULT_BODY_LENGTH
                    );

                    // Apply stretching force along the BH direction
                    if (physics.body != null && dist > 0f) {
                        toBH.nor();
                        stretchForce.set(toBH).scl(tidalAcc * physics.mass);
                        physics.body.applyCentralForce(stretchForce);
                    }

                    // Publish damage event if threshold exceeded
                    if (tidalAcc > TIDAL_DAMAGE_THRESHOLD) {
                        eventBus.publish(new TidalDamageEvent(body, tidalAcc));
                    }
                }
            }
        } finally {
            Pools.free(toBH);
            Pools.free(stretchForce);
        }
    }

    /**
     * Computes the differential tidal acceleration across a body of the
     * given length at distance r from a gravitational source.
     *
     * @param gm       G * M (gravitational parameter)
     * @param r        distance from the black hole centre (clamped >= rs)
     * @param bodyLength approximate extent of the body along the radial axis
     * @return tidal acceleration magnitude in m/s^2
     */
    public static float computeTidalAcceleration(float gm, float r, float bodyLength) {
        return 2f * gm * bodyLength / (r * r * r);
    }
}
