package com.galacticodyssey.water;

/**
 * A single Gerstner wave contribution. Multiple of these are composed on a
 * {@link WaterBodyComponent} to produce the final surface.
 *
 * <p>For deep water, speed should satisfy the dispersion relation:
 * {@code speed = sqrt(g * wavelength / (2 * PI))}.
 */
public final class WaveParams {

    /** Wave amplitude in metres (peak-to-trough / 2). */
    public float amplitude;

    /** Wavelength in metres. */
    public float wavelength;

    /** Phase speed in m/s. */
    public float speed;

    /** Gerstner steepness Q in [0, 1]. 0 = pure sine, 1 = breaking crest. */
    public float steepness;

    /** Propagation direction in degrees (0 = +X axis). */
    public float directionDeg;
}
