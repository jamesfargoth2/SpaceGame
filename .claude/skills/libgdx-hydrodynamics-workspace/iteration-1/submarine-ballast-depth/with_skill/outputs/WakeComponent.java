package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Wake trail data for rendering. The Kelvin wake pattern trails behind
 * surface vessels; fully submerged vessels produce no wake.
 *
 * <p>Rendering code reads {@link #wakeTrail} and {@link #wakeIntensity}
 * to draw foam geometry. Physics systems write {@link #froudeNumber}
 * each tick.
 */
public class WakeComponent implements Component {

    /** Kelvin half-angle in radians (~19.47 degrees). */
    public float kelvinAngleRad = 0.3398f;

    /** Wake strength in [0, 1] based on Froude number. */
    public float wakeIntensity;

    /** Froude number: v / sqrt(g * L). Determines wave-making drag hump. */
    public float froudeNumber;

    /** Trail of recent world-space positions for wake geometry. */
    public final Array<Vector3> wakeTrail = new Array<>(64);
}
