package com.galacticodyssey.water;

import com.badlogic.gdx.math.Vector3;

/**
 * A single hull surface patch used for buoyancy and drag sampling. Each point
 * represents a small area of the hull and has an outward-facing normal in body
 * frame.
 *
 * <p>Place 8–32 points across the hull, concentrated at bow, stern, and beam
 * edges where the waterline varies most during pitch and roll.
 */
public final class BuoyancySamplePoint {

    /** Offset from the entity centre in body (local) frame. */
    public final Vector3 localOffset = new Vector3();

    /** Outward hull normal in body frame. */
    public final Vector3 normal = new Vector3();

    /** Area of the hull patch this point represents, in m². */
    public float area;

    /** Current submersion depth in metres (written by BuoyancySystem each tick). */
    public float depth;

    /** Whether this point is currently below the water surface. */
    public boolean submerged;
}
