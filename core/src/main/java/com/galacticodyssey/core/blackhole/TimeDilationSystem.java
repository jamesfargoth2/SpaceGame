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
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Computes the Schwarzschild time dilation factor for entities with a
 * {@link TimeDilationComponent} near black holes.
 *
 * <p>The dilation factor is:
 * <pre>
 *   factor = sqrt(max(0, 1 - rs / r))
 * </pre>
 * where rs is the Schwarzschild radius and r is the distance from the black
 * hole. The factor is clamped to a floor of
 * {@link BlackHoleComponent#maxTimeDilation} to prevent entities from
 * freezing completely.
 *
 * <p>Other systems should multiply their local {@code deltaTime} by
 * {@link TimeDilationComponent#timeDilationFactor} to implement per-entity
 * time slowing. This system does not slow global simulation.
 *
 * <p>Priority 3 (runs after EventHorizonSystem and TidalForceSystem).
 */
public class TimeDilationSystem extends EntitySystem {

    public static final int PRIORITY = 3;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BlackHoleComponent> BLACK_HOLE_M =
        ComponentMapper.getFor(BlackHoleComponent.class);
    private static final ComponentMapper<TimeDilationComponent> DILATION_M =
        ComponentMapper.getFor(TimeDilationComponent.class);

    private static final Family BLACK_HOLE_FAMILY = Family.all(
        TransformComponent.class, BlackHoleComponent.class
    ).get();

    private static final Family DILATED_FAMILY = Family.all(
        TransformComponent.class, TimeDilationComponent.class
    ).get();

    private final EventBus eventBus;

    private ImmutableArray<Entity> blackHoles;
    private ImmutableArray<Entity> dilatedEntities;

    public TimeDilationSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        blackHoles = engine.getEntitiesFor(BLACK_HOLE_FAMILY);
        dilatedEntities = engine.getEntitiesFor(DILATED_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        blackHoles = null;
        dilatedEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (blackHoles.size() == 0) return;

        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < dilatedEntities.size(); i++) {
                Entity entity = dilatedEntities.get(i);
                TransformComponent entityTransform = TRANSFORM_M.get(entity);
                TimeDilationComponent dilation = DILATION_M.get(entity);

                // Reset flags; EventHorizonSystem sets isInsideISCO before us
                // so we preserve it, but we own isInPhotonSphere
                dilation.isInPhotonSphere = false;

                // Find the strongest dilation (closest black hole)
                float strongestFactor = 1f;

                for (int j = 0; j < blackHoles.size(); j++) {
                    Entity bhEntity = blackHoles.get(j);
                    BlackHoleComponent bh = BLACK_HOLE_M.get(bhEntity);
                    TransformComponent bhTransform = TRANSFORM_M.get(bhEntity);

                    tmp.set(entityTransform.position).sub(bhTransform.position);
                    float r = tmp.len();

                    // Outside the falloff radius: no dilation from this BH
                    if (r > bh.dilationFalloffRadius) continue;

                    float factor = computeDilationFactor(r, bh.schwarzschildRadius);
                    factor = Math.max(factor, bh.maxTimeDilation);

                    // Keep the strongest dilation (lowest factor)
                    if (factor < strongestFactor) {
                        strongestFactor = factor;
                    }

                    // Photon sphere check
                    if (r <= bh.photonSphere) {
                        dilation.isInPhotonSphere = true;
                    }
                }

                dilation.timeDilationFactor = strongestFactor;
            }
        } finally {
            Pools.free(tmp);
        }
    }

    /**
     * Computes the Schwarzschild time dilation factor at distance r.
     *
     * @param r  distance from the black hole centre
     * @param rs Schwarzschild radius
     * @return dilation factor in [0, 1] where 1 = normal time
     */
    public static float computeDilationFactor(float r, float rs) {
        if (r <= rs) return 0f;
        return (float) Math.sqrt(Math.max(0f, 1f - rs / r));
    }
}
