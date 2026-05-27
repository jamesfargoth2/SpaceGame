package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;

/**
 * PID-based depth autopilot for the submarine.
 * The player sets a target depth and this controller computes
 * the desired ballast fill fraction to reach and hold that depth.
 */
public class DepthControlComponent implements Component {

    /** Target depth in meters (positive = deeper). */
    public float targetDepth = 0f;

    /** Current depth in meters (positive = deeper, computed by BuoyancySystem). */
    public float currentDepth = 0f;

    /** Whether the depth autopilot is engaged. */
    public boolean engaged = false;

    /** Maximum descent rate in meters per second. */
    public float maxDescentRate = 5f;

    /** Maximum ascent rate in meters per second. */
    public float maxAscentRate = 8f;

    /** Current vertical speed in m/s (positive = descending). */
    public float verticalSpeed = 0f;

    // --- PID controller gains ---

    /** Proportional gain. */
    public float kP = 0.02f;

    /** Integral gain. */
    public float kI = 0.001f;

    /** Derivative gain. */
    public float kD = 0.05f;

    // --- PID internal state ---

    /** Accumulated integral error. */
    public float integralError = 0f;

    /** Previous frame's error (for derivative). */
    public float previousError = 0f;

    /** Anti-windup clamp for the integral term. */
    public float integralClamp = 2f;

    /** Output of the PID controller (desired ballast fill fraction delta, -1 to 1). */
    public float controlOutput = 0f;

    /** Resets PID state (call when disengaging/re-engaging). */
    public void resetPID() {
        integralError = 0f;
        previousError = 0f;
        controlOutput = 0f;
    }
}
