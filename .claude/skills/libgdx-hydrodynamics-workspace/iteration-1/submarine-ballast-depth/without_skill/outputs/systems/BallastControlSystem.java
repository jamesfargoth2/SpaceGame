package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.components.BallastTankComponent;
import com.galacticodyssey.water.components.BallastTankComponent.Tank;
import com.galacticodyssey.water.components.DepthControlComponent;
import com.galacticodyssey.water.events.BallastChangedEvent;

/**
 * Manages ballast tank fill and drain operations.
 *
 * In auto mode, the DepthControlSystem's PID output drives the target fill fraction.
 * This system adjusts individual tank valves to reach that target, respecting
 * per-tank flow rates. Emergency blow overrides everything and drains all tanks
 * at maximum rate.
 */
public class BallastControlSystem extends IteratingSystem {

    private static final int PRIORITY = 7;
    private static final float BALLAST_EVENT_THRESHOLD = 0.02f;

    private final ComponentMapper<BallastTankComponent> ballastMapper =
        ComponentMapper.getFor(BallastTankComponent.class);
    private final ComponentMapper<DepthControlComponent> depthMapper =
        ComponentMapper.getFor(DepthControlComponent.class);

    private final EventBus eventBus;

    /** Tracks the previous fill fraction to gate event publishing. */
    private float previousFillFraction = -1f;

    public BallastControlSystem(EventBus eventBus) {
        super(Family.all(BallastTankComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final BallastTankComponent ballast = ballastMapper.get(entity);
        final DepthControlComponent depth = depthMapper.get(entity);

        if (ballast.emergencyBlow) {
            processEmergencyBlow(entity, ballast, deltaTime);
            return;
        }

        if (ballast.autoControl && depth != null && depth.engaged) {
            // The PID controller output is mapped to a target fill fraction
            ballast.targetFillFraction = MathUtils.clamp(
                0.5f + depth.controlOutput, 0f, 1f);
        }

        // Adjust each tank toward the target fill fraction
        for (int i = 0; i < ballast.tanks.size; i++) {
            Tank tank = ballast.tanks.get(i);
            float targetVol = ballast.targetFillFraction * tank.capacity;
            float diff = targetVol - tank.currentVolume;

            if (Math.abs(diff) < 0.001f) {
                tank.filling = false;
                tank.blowing = false;
                continue;
            }

            float maxChange = tank.flowRate * deltaTime;

            if (diff > 0) {
                // Need to fill
                tank.filling = true;
                tank.blowing = false;
                tank.currentVolume = Math.min(tank.currentVolume + maxChange, targetVol);
            } else {
                // Need to blow
                tank.filling = false;
                tank.blowing = true;
                tank.currentVolume = Math.max(tank.currentVolume - maxChange, targetVol);
            }

            // Clamp to valid range
            tank.currentVolume = MathUtils.clamp(tank.currentVolume, 0f, tank.capacity);
        }

        publishBallastEventIfChanged(entity, ballast);
    }

    /**
     * Emergency blow: drain all tanks as fast as possible to surface.
     */
    private void processEmergencyBlow(Entity entity, BallastTankComponent ballast, float deltaTime) {
        boolean anyWaterLeft = false;

        for (int i = 0; i < ballast.tanks.size; i++) {
            Tank tank = ballast.tanks.get(i);
            if (tank.currentVolume > 0f) {
                float drainRate = tank.flowRate * ballast.emergencyBlowMultiplier;
                tank.currentVolume = Math.max(0f, tank.currentVolume - drainRate * deltaTime);
                tank.filling = false;
                tank.blowing = true;
                if (tank.currentVolume > 0f) {
                    anyWaterLeft = true;
                }
            } else {
                tank.blowing = false;
            }
        }

        // Auto-disengage emergency blow when all tanks are empty
        if (!anyWaterLeft) {
            ballast.emergencyBlow = false;
        }

        publishBallastEventIfChanged(entity, ballast);
    }

    private void publishBallastEventIfChanged(Entity entity, BallastTankComponent ballast) {
        float currentFill = ballast.getOverallFillFraction();
        if (previousFillFraction < 0f || Math.abs(currentFill - previousFillFraction) > BALLAST_EVENT_THRESHOLD) {
            previousFillFraction = currentFill;
            eventBus.publish(new BallastChangedEvent(
                entity, currentFill, ballast.getTotalWaterMass(), ballast.emergencyBlow));
        }
    }
}
