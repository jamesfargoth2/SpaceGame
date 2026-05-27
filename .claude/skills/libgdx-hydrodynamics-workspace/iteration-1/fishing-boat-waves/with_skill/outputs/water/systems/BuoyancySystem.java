package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.BuoyancySamplePoint;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.WaterBodyComponent;
import com.galacticodyssey.water.events.VesselEnteredWaterEvent;
import com.galacticodyssey.water.events.VesselExitedWaterEvent;

/**
 * Computes buoyancy forces for every entity with a {@link HullComponent} and
 * a {@link PhysicsBodyComponent}. For each hull sample point, hydrostatic
 * pressure is integrated over the submerged patch area and applied via the
 * point's world-space hull normal. This produces natural restoring torques
 * for roll and pitch stability without explicit metacentric height
 * calculations.
 *
 * <p>Forces are applied through Bullet's {@code applyCentralForce} and
 * {@code applyTorque} — never bypassing the physics engine.
 *
 * <p>The force direction follows each sample point's <strong>world-space hull
 * normal</strong>, not just world-up. A hull tilted so one side sits deeper
 * receives more upward force on that side, producing emergent righting
 * torques.
 */
public class BuoyancySystem extends IteratingSystem {

    private final ComponentMapper<HullComponent> hullMapper =
            ComponentMapper.getFor(HullComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final CoordinateManager originManager;
    private final WaveSystem waveSystem;
    private final EventBus eventBus;

    /** The active water body. Set externally when the player enters a water zone. */
    private WaterBodyComponent waterBody;

    /** Entity that owns the current waterBody component (for event payload). */
    private Entity waterBodyEntity;

    /**
     * @param priority   Ashley system priority (should run after WaveSystem)
     * @param originMgr  coordinate manager for galaxy-space conversion
     * @param waveSystem the wave system to query surface heights from
     * @param eventBus   event bus for publishing water entry/exit events
     */
    public BuoyancySystem(int priority, CoordinateManager originMgr,
                          WaveSystem waveSystem, EventBus eventBus) {
        super(Family.all(HullComponent.class, PhysicsBodyComponent.class).get(), priority);
        this.originManager = originMgr;
        this.waveSystem = waveSystem;
        this.eventBus = eventBus;
    }

    /**
     * Sets the active water body that all vessels interact with.
     *
     * @param waterBody       the water body component
     * @param waterBodyEntity the entity owning the water body
     */
    public void setWaterBody(WaterBodyComponent waterBody, Entity waterBodyEntity) {
        this.waterBody = waterBody;
        this.waterBodyEntity = waterBodyEntity;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        if (waterBody == null) return;

        final HullComponent hull = hullMapper.get(entity);
        final PhysicsBodyComponent physBody = physicsMapper.get(entity);
        if (physBody.body == null) return;

        final Matrix4 worldTx = new Matrix4();
        physBody.body.getWorldTransform(worldTx);

        final Vector3 worldPt = Pools.obtain(Vector3.class);
        final Vector3 force = Pools.obtain(Vector3.class);
        final Vector3 lever = Pools.obtain(Vector3.class);
        final Vector3 torque = Pools.obtain(Vector3.class).setZero();
        final Vector3 totalForce = Pools.obtain(Vector3.class).setZero();
        final Vector3 comPos = Pools.obtain(Vector3.class);
        physBody.body.getCenterOfMassTransform().getTranslation(comPos);

        final int prevSubmergedCount = countSubmerged(hull);

        for (int i = 0; i < hull.samplePoints.size; i++) {
            BuoyancySamplePoint sp = hull.samplePoints.get(i);

            // Transform sample point to world space
            worldPt.set(sp.localOffset).mul(worldTx);

            // Convert to galaxy-space (doubles) for wave evaluation — wave phase
            // uses galaxy-space so waves tile seamlessly across origin rebases
            double gx = originManager.getOriginOffsetX() + worldPt.x;
            double gz = originManager.getOriginOffsetZ() + worldPt.z;
            float surfaceY = waveSystem.getHeight(gx, gz);

            sp.depth = surfaceY - worldPt.y;
            sp.submerged = sp.depth > 0f;
            if (!sp.submerged) continue;

            // Hydrostatic pressure force on this hull patch:
            // F = rho * g * depth * area, directed along the hull normal
            float pressure = waterBody.density * waveSystem.getGravity() * sp.depth;
            force.set(sp.normal).rot(worldTx).scl(pressure * sp.area);
            totalForce.add(force);

            // Torque about centre of mass: tau = r x F
            lever.set(worldPt).sub(comPos);
            torque.x += lever.y * force.z - lever.z * force.y;
            torque.y += lever.z * force.x - lever.x * force.z;
            torque.z += lever.x * force.y - lever.y * force.x;
        }

        if (!totalForce.isZero()) {
            physBody.body.applyCentralForce(totalForce);
            physBody.body.applyTorque(torque);
        }

        // Publish water entry/exit events
        int currentSubmergedCount = countSubmerged(hull);
        if (prevSubmergedCount == 0 && currentSubmergedCount > 0) {
            eventBus.publish(new VesselEnteredWaterEvent(entity, waterBodyEntity));
        } else if (prevSubmergedCount > 0 && currentSubmergedCount == 0) {
            eventBus.publish(new VesselExitedWaterEvent(entity));
        }

        Pools.free(worldPt);
        Pools.free(force);
        Pools.free(lever);
        Pools.free(torque);
        Pools.free(totalForce);
        Pools.free(comPos);
    }

    /**
     * Returns true if every sample point on the hull is submerged.
     */
    public static boolean isFullySubmerged(HullComponent hull) {
        for (int i = 0; i < hull.samplePoints.size; i++) {
            if (!hull.samplePoints.get(i).submerged) return false;
        }
        return true;
    }

    private int countSubmerged(HullComponent hull) {
        int count = 0;
        for (int i = 0; i < hull.samplePoints.size; i++) {
            if (hull.samplePoints.get(i).submerged) count++;
        }
        return count;
    }
}
