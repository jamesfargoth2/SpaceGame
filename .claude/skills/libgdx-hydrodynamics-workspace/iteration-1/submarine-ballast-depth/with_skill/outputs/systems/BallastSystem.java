package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.BallastComponent;
import com.galacticodyssey.water.BallastTank;
import com.galacticodyssey.water.HullComponent;
import com.galacticodyssey.water.WaterBodyComponent;
import com.galacticodyssey.water.events.SubmarineHullBreachEvent;

/**
 * Submarine depth control via a PID controller driving ballast tank
 * fill/drain. The player sets a target depth on the {@link BallastComponent}
 * and this system automatically adjusts ballast to reach and hold that depth.
 *
 * <p>When current depth exceeds the hull's crush depth, a
 * {@link SubmarineHullBreachEvent} is published. The flooding system listens
 * for this to create breaches in compartments.
 *
 * <p>The PID controller includes a dead-band around the target depth to
 * prevent oscillation near the surface or at target depth. The integral
 * term is clamped to prevent wind-up.
 *
 * <p>The rigid body's mass is updated each tick via
 * {@code btRigidBody.setMassProps} whenever ballast fill changes, since
 * Bullet caches mass and inertia internally.
 */
public class BallastSystem extends IteratingSystem {

    private final ComponentMapper<BallastComponent> ballastMapper =
            ComponentMapper.getFor(BallastComponent.class);
    private final ComponentMapper<HullComponent> hullMapper =
            ComponentMapper.getFor(HullComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final CoordinateManager originManager;
    private final WaveSystem waveSystem;
    private final EventBus eventBus;

    /** The active water body. Set externally. */
    private WaterBodyComponent waterBody;

    /** Tracks whether we already published a breach event per entity. */
    private boolean breachPublished = false;

    /**
     * @param priority   Ashley system priority (should run after drag system)
     * @param originMgr  coordinate manager for depth computation
     * @param waveSystem the wave system for surface height queries
     * @param eventBus   event bus for hull breach events
     */
    public BallastSystem(int priority, CoordinateManager originMgr,
                         WaveSystem waveSystem, EventBus eventBus) {
        super(Family.all(BallastComponent.class, HullComponent.class,
                         PhysicsBodyComponent.class).get(), priority);
        this.originManager = originMgr;
        this.waveSystem = waveSystem;
        this.eventBus = eventBus;
    }

    /**
     * Sets the active water body for density lookups and depth reference.
     *
     * @param waterBody the water body component
     */
    public void setWaterBody(WaterBodyComponent waterBody) {
        this.waterBody = waterBody;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        if (waterBody == null) return;

        final BallastComponent ballast = ballastMapper.get(entity);
        final HullComponent hull = hullMapper.get(entity);
        final PhysicsBodyComponent physBody = physicsMapper.get(entity);
        if (physBody.body == null) return;

        float currentDepth = getCurrentDepth(entity, physBody);
        float error = ballast.targetDepth - currentDepth;

        // Dead-band: zero PID output when within tolerance to prevent
        // oscillation at surface or at target depth
        float command;
        if (Math.abs(error) < ballast.deadBand) {
            command = 0f;
            // Don't accumulate integral in dead-band
        } else {
            // PID controller
            ballast.depthIntegral += error * dt;
            ballast.depthIntegral = MathUtils.clamp(ballast.depthIntegral, -10f, 10f);
            float dError = (error - ballast.prevError) / Math.max(dt, 0.001f);
            ballast.prevError = error;

            command = ballast.depthKp * error
                    + ballast.depthKi * ballast.depthIntegral
                    + ballast.depthKd * dError;
        }

        // Positive command = go deeper = flood tanks; negative = blow tanks
        for (int i = 0; i < ballast.tanks.size; i++) {
            BallastTank tank = ballast.tanks.get(i);
            if (command > 0f) {
                tank.currentFill = Math.min(tank.capacity,
                        tank.currentFill + tank.fillRate * dt);
            } else if (command < 0f) {
                tank.currentFill = Math.max(0f,
                        tank.currentFill - tank.drainRate * dt);
            }
            // If command is zero (in dead-band), tanks hold current fill
        }

        // Update rigid body mass from ballast water
        float ballastMass = getTotalBallastMass(ballast);
        Vector3 inertia = Pools.obtain(Vector3.class);
        inertia.set(physBody.body.getLocalInertia());
        physBody.body.setMassProps(hull.dryMass + ballastMass, inertia);
        Pools.free(inertia);

        // Hull breach check: when deeper than crush depth
        if (currentDepth > hull.crushDepth) {
            if (!breachPublished) {
                eventBus.publish(new SubmarineHullBreachEvent(entity, currentDepth, hull.crushDepth));
                breachPublished = true;
            }
        } else {
            breachPublished = false;
        }
    }

    /**
     * Computes the current depth below the water surface in metres.
     * Uses the wave surface height at the entity's galaxy-space position.
     */
    private float getCurrentDepth(Entity entity, PhysicsBodyComponent physBody) {
        Vector3 pos = Pools.obtain(Vector3.class);
        physBody.body.getCenterOfMassTransform().getTranslation(pos);

        double gx = originManager.getOriginOffsetX() + pos.x;
        double gz = originManager.getOriginOffsetZ() + pos.z;
        float surfaceY = waveSystem.getHeight(gx, gz);

        float depth = surfaceY - pos.y;
        Pools.free(pos);
        return Math.max(depth, 0f);
    }

    /**
     * Computes total ballast water mass across all tanks.
     */
    private float getTotalBallastMass(BallastComponent ballast) {
        if (waterBody == null) return 0f;
        float totalVolume = 0f;
        for (int i = 0; i < ballast.tanks.size; i++) {
            totalVolume += ballast.tanks.get(i).currentFill;
        }
        return totalVolume * waterBody.density;
    }
}
