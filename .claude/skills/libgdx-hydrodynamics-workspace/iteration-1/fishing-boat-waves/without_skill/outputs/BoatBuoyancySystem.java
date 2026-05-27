package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.BuoyancySamplePoint;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.WaterBodyComponent;

/**
 * Per-point buoyancy system for surface vessels (boats, ships, floating debris).
 *
 * <p>For each entity with a {@link HullComponent} and {@link PhysicsBodyComponent},
 * this system iterates over the hull's {@link BuoyancySamplePoint}s, transforms each
 * to world space, queries the wave surface height at that point's galaxy-space
 * coordinates, and applies an upward buoyancy force proportional to submersion
 * depth. The force direction follows the hull normal at each sample point, which
 * produces natural restoring torques for pitch, roll, and heave.
 *
 * <p>Unlike the submarine {@link BuoyancySystem}, this system does not compute a
 * single submerged fraction. Instead, each hull patch contributes independently,
 * giving the boat a physically-motivated response to wave geometry.
 *
 * <p>Requires a {@link WaveSystem} to be present in the engine for wave height
 * queries, and a {@link WaterBodyComponent} entity for density. The
 * {@link CoordinateManager} converts local positions to galaxy-space so that wave
 * phase is stable across floating-origin rebases.
 */
public class BoatBuoyancySystem extends IteratingSystem {

    private static final int PRIORITY = 6;

    private final ComponentMapper<HullComponent> hullMapper =
        ComponentMapper.getFor(HullComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final CoordinateManager coordinateManager;
    private final WaveSystem waveSystem;
    private final WaterBodyComponent waterBody;

    private final Matrix4 tempTransform = new Matrix4();

    /**
     * @param coordinateManager converts local to galaxy-space for wave queries
     * @param waveSystem        service system that evaluates wave height
     * @param waterBody         the water body component (read for density)
     */
    public BoatBuoyancySystem(CoordinateManager coordinateManager,
                               WaveSystem waveSystem,
                               WaterBodyComponent waterBody) {
        super(Family.all(HullComponent.class, PhysicsBodyComponent.class).get(), PRIORITY);
        this.coordinateManager = coordinateManager;
        this.waveSystem = waveSystem;
        this.waterBody = waterBody;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final HullComponent hull = hullMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);

        if (physics.body == null) return;

        // Get the body's world transform
        physics.body.getWorldTransform(tempTransform);

        // Obtain pooled temporaries
        Vector3 worldPos = Pools.obtain(Vector3.class);
        Vector3 worldNormal = Pools.obtain(Vector3.class);
        Vector3 force = Pools.obtain(Vector3.class);
        Vector3 bodyCenter = Pools.obtain(Vector3.class);
        Vector3 relPos = Pools.obtain(Vector3.class);

        tempTransform.getTranslation(bodyCenter);

        float gravity = waveSystem.getGravity();
        float density = waterBody.density;

        try {
            for (int i = 0; i < hull.samplePoints.size; i++) {
                BuoyancySamplePoint sample = hull.samplePoints.get(i);

                // Transform sample point from hull-local to world space
                worldPos.set(sample.localOffset);
                worldPos.mul(tempTransform);

                // Transform hull normal from body frame to world frame (rotation only)
                worldNormal.set(sample.normal);
                worldNormal.rot(tempTransform);

                // Convert world position to galaxy-space for wave query
                double[] galaxy = coordinateManager.toGalaxySpace(worldPos);
                double gx = galaxy[0];
                double gz = galaxy[2];

                // Query wave surface height at this point
                float surfaceHeight = waveSystem.getHeight(gx, gz);

                // Compute submersion depth (positive = submerged)
                float depth = surfaceHeight - worldPos.y;
                sample.depth = depth;
                sample.submerged = depth > 0f;

                if (depth <= 0f) continue;

                // Buoyancy force magnitude: rho * g * depth * area
                // (depth * area approximates submerged volume for this patch)
                float forceMagnitude = density * gravity * depth * sample.area;

                // Apply force along the hull normal direction (not just world-up)
                // This produces natural restoring torques when the hull is tilted
                force.set(worldNormal).scl(forceMagnitude);

                // Apply force at the sample point position (creates torques)
                relPos.set(worldPos).sub(bodyCenter);
                physics.body.applyForce(force, relPos);
            }
        } finally {
            Pools.free(worldPos);
            Pools.free(worldNormal);
            Pools.free(force);
            Pools.free(bodyCenter);
            Pools.free(relPos);
        }
    }
}
