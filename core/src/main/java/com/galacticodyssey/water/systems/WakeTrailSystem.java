package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.WakeComponent;

/**
 * Updates the wake trail behind moving surface vessels for rendering.
 * <p>
 * Each frame, if the vessel is moving fast enough (wake intensity &gt; 0),
 * the stern position is appended to the {@link WakeComponent#wakeTrail}.
 * Old trail points are evicted when the trail exceeds its maximum length,
 * producing a moving window of recent positions for foam/wake rendering.
 * <p>
 * The rendering system reads the trail and {@link WakeComponent#wakeIntensity}
 * (written by {@link HydrodynamicDragSystem}) to draw Kelvin wake geometry.
 * <p>
 * Runs at priority 13 (after motor and drag systems).
 */
public class WakeTrailSystem extends EntitySystem {

    /** Maximum number of trail points to retain. */
    private static final int MAX_TRAIL_LENGTH = 64;

    /** Minimum interval in seconds between trail point emissions. */
    private static final float EMIT_INTERVAL = 0.1f;

    /** Minimum wake intensity required to emit a trail point. */
    private static final float MIN_WAKE_INTENSITY = 0.05f;

    private static final ComponentMapper<WakeComponent> wakeMapper =
        ComponentMapper.getFor(WakeComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<HullComponent> hullMapper =
        ComponentMapper.getFor(HullComponent.class);

    private ImmutableArray<Entity> wakeEntities;
    private float timeSinceEmit;

    private final Matrix4 tempTransform = new Matrix4();

    public WakeTrailSystem() {
        super(17);
    }

    @Override
    public void addedToEngine(Engine engine) {
        wakeEntities = engine.getEntitiesFor(Family.all(
            WakeComponent.class,
            PhysicsBodyComponent.class,
            HullComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        timeSinceEmit += deltaTime;
        if (timeSinceEmit < EMIT_INTERVAL) return;
        timeSinceEmit = 0f;

        for (int i = 0; i < wakeEntities.size(); i++) {
            processEntity(wakeEntities.get(i));
        }
    }

    private void processEntity(Entity entity) {
        final WakeComponent wake = wakeMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        final HullComponent hull = hullMapper.get(entity);
        if (physics.body == null) return;

        // Only emit wake points when moving fast enough
        if (wake.wakeIntensity < MIN_WAKE_INTENSITY) {
            // Fade out existing trail by removing oldest points
            if (wake.wakeTrail.size > 0) {
                wake.wakeTrail.removeIndex(0);
            }
            return;
        }

        // Compute stern position in world space.
        // Stern is at local (0, 0, +hullLength/2) assuming -Z is forward.
        physics.body.getWorldTransform(tempTransform);
        Vector3 sternLocal = Pools.obtain(Vector3.class);
        Vector3 sternWorld = Pools.obtain(Vector3.class);

        sternLocal.set(0f, 0f, hull.hullLength * 0.5f);
        sternWorld.set(sternLocal).mul(tempTransform);

        // Append to trail (allocate a new Vector3 for storage)
        wake.wakeTrail.add(new Vector3(sternWorld));

        // Evict oldest points if trail is too long
        while (wake.wakeTrail.size > MAX_TRAIL_LENGTH) {
            wake.wakeTrail.removeIndex(0);
        }

        Pools.free(sternLocal);
        Pools.free(sternWorld);
    }
}
