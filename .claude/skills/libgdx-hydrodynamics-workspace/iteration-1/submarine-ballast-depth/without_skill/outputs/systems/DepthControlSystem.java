package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.water.components.DepthControlComponent;
import com.galacticodyssey.water.components.SubmarineStateComponent;

/**
 * PID controller that maintains a target depth by adjusting ballast.
 *
 * The controller computes an output value [-1, 1] representing how much
 * to fill (positive) or blow (negative) ballast. The BallastControlSystem
 * reads this output and adjusts individual tanks accordingly.
 *
 * The PID loop:
 *   error = targetDepth - currentDepth
 *   P = kP * error
 *   I = kI * integral(error * dt), clamped by integralClamp
 *   D = kD * d(error)/dt
 *   output = clamp(P + I + D, -1, 1)
 */
public class DepthControlSystem extends IteratingSystem {

    private static final int PRIORITY = 6;

    private final ComponentMapper<DepthControlComponent> depthMapper =
        ComponentMapper.getFor(DepthControlComponent.class);
    private final ComponentMapper<SubmarineStateComponent> stateMapper =
        ComponentMapper.getFor(SubmarineStateComponent.class);

    public DepthControlSystem() {
        super(Family.all(DepthControlComponent.class).get(), PRIORITY);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final DepthControlComponent depth = depthMapper.get(entity);
        final SubmarineStateComponent state = stateMapper.get(entity);

        if (!depth.engaged) {
            depth.resetPID();
            return;
        }

        // Don't run depth control if the submarine is in a critical state
        if (state != null && state.criticalFailure) {
            depth.engaged = false;
            depth.resetPID();
            return;
        }

        // Clamp target depth to safe ranges
        float effectiveTarget = depth.targetDepth;
        if (effectiveTarget < 0f) {
            effectiveTarget = 0f;
        }

        // PID error
        float error = effectiveTarget - depth.currentDepth;

        // Rate-limit the error to respect max descent/ascent rates
        // Positive error = need to go deeper, negative = need to go shallower
        float maxDepthChange = (error > 0f ? depth.maxDescentRate : depth.maxAscentRate) * deltaTime;
        if (Math.abs(error) > maxDepthChange * 10f) {
            // Large error: don't let the integral wind up too aggressively
            error = Math.signum(error) * maxDepthChange * 10f;
        }

        // Proportional term
        float pTerm = depth.kP * error;

        // Integral term (with anti-windup)
        depth.integralError += error * deltaTime;
        depth.integralError = MathUtils.clamp(depth.integralError,
            -depth.integralClamp, depth.integralClamp);
        float iTerm = depth.kI * depth.integralError;

        // Derivative term
        float dError = (error - depth.previousError) / Math.max(deltaTime, 0.001f);
        float dTerm = depth.kD * dError;
        depth.previousError = error;

        // Combined output
        depth.controlOutput = MathUtils.clamp(pTerm + iTerm + dTerm, -1f, 1f);
    }
}
