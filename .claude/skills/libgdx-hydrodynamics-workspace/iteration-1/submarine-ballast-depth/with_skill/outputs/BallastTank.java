package com.galacticodyssey.water;

import com.badlogic.gdx.math.Vector3;

/**
 * A single ballast tank on a submarine. Fill state affects vessel mass and
 * trim; the tank's local position determines which end of the hull gets
 * heavier as it fills.
 */
public final class BallastTank {

    /** Maximum water capacity of this tank in m³. */
    public float capacity;

    /** Current water volume in the tank in m³. */
    public float currentFill;

    /** Pump speed — rate of filling from sea water in m³/s. */
    public float fillRate;

    /** Blow speed — rate of draining compressed air forces water out in m³/s. */
    public float drainRate;

    /** Position of the tank centre in hull body frame. Affects trim when filling. */
    public final Vector3 localPosition = new Vector3();
}
