package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;

/**
 * Motor and steering properties for a surface vessel. Attached alongside a
 * {@link com.galacticodyssey.water.HullComponent} and
 * {@link com.galacticodyssey.core.components.PhysicsBodyComponent} to give a
 * vessel propulsion and rudder control.
 * <p>
 * All values should be loaded from data files; defaults here are tuned for a
 * small fishing boat (~12 m, ~5000 kg).
 */
public class BoatMotorComponent implements Component {

    /** Maximum forward thrust force in Newtons. */
    public float maxThrust = 8000f;

    /** Maximum reverse thrust force in Newtons (usually weaker than forward). */
    public float maxReverseThrust = 3000f;

    /** Yaw torque applied at full rudder deflection, in N*m. */
    public float rudderTorque = 25000f;

    /**
     * How quickly throttle responds to input changes (units/s).
     * A value of 2.0 means it takes ~0.5 s to go from 0 to full throttle.
     */
    public float throttleResponseRate = 2.0f;

    /**
     * How quickly the rudder responds to steering input changes (units/s).
     * Higher values give a snappier feel; lower values feel heavier.
     */
    public float rudderResponseRate = 1.5f;

    /** Speed-dependent rudder effectiveness factor. At low speed the rudder is weaker. */
    public float minSpeedForFullRudder = 3.0f;

    /** Current smoothed throttle value in [-1, 1] (written by BoatMotorSystem). */
    public float currentThrottle;

    /** Current smoothed rudder deflection in [-1, 1] (written by BoatMotorSystem). */
    public float currentRudder;
}
