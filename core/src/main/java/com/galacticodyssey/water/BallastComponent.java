package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Submarine depth-control state. A PID controller drives the ballast tanks
 * to maintain {@link #targetDepth}.
 */
public class BallastComponent implements Component {

    /** All ballast tanks on this vessel. */
    public final Array<BallastTank> tanks = new Array<>();

    /** Desired depth below the water surface in metres (0 = surface). */
    public float targetDepth = 0f;

    /** PID proportional gain for depth controller. */
    public float depthKp = 2000f;

    /** PID derivative gain for depth controller. */
    public float depthKd = 800f;

    /** PID integral gain for depth controller. */
    public float depthKi = 50f;

    /** Accumulated integral error for the PID controller. */
    public float depthIntegral;

    /** Previous tick's error value (for derivative term). */
    public float prevError;

    /**
     * Dead-band radius around the target depth in metres. The PID output is
     * zeroed when the error is within this range to prevent oscillation at
     * the surface or at target depth.
     */
    public float deadBand = 0.5f;
}
