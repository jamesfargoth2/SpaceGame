package com.galacticodyssey.core.blackhole;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Marks an entity as a black hole with Schwarzschild geometry.
 *
 * <p>Use the {@link #create(float)} factory to populate derived radii from mass.
 * The gravitational pull itself is handled by the existing GravitySystem via
 * {@link com.galacticodyssey.core.components.GravitySourceComponent}; this
 * component adds the relativistic riders: event horizon, tidal forces, and
 * time dilation.
 *
 * <p><strong>Mass scaling:</strong> Real-world G and c squared are used, so mass must be
 * scaled to produce a schwarzschildRadius that is meaningful at the game's
 * distance scale. For instance, mass ~6.7e26 yields rs ~1.0 game-unit; tune
 * upward to get a visually significant kill zone (rs 50-500 units).
 */
public class BlackHoleComponent implements Component, Pool.Poolable {

    /** Gravitational constant (SI: m^3 kg^-1 s^-2). */
    public static final float G = 6.674e-11f;

    /** Speed of light squared (SI: m^2/s^2). */
    public static final float C_SQUARED = 9e16f;

    /** Black hole mass in game-scale kilograms. */
    public float mass;

    /** Schwarzschild radius: rs = 2GM/c^2. Entity is destroyed if r <= rs. */
    public float schwarzschildRadius;

    /** Innermost stable circular orbit = 3 * rs. No stable orbit below this. */
    public float innerStableOrbit;

    /** Photon sphere radius = 1.5 * rs. Visual lensing cue threshold. */
    public float photonSphere;

    /** Inner edge of the accretion disk = 3 * rs. */
    public float accretionDiskInner;

    /** Outer edge of the accretion disk = 20 * rs. */
    public float accretionDiskOuter;

    /** Dimensionless spin parameter (0 = Schwarzschild, 1 = extremal Kerr). */
    public float spin;

    /**
     * Floor for the time dilation factor near the horizon. Prevents entities
     * from freezing completely (factor = 0). Typical value: 0.05 - 0.3.
     */
    public float maxTimeDilation = 0.1f;

    /** Radius beyond which time dilation factor is clamped to 1.0. */
    public float dilationFalloffRadius;

    /**
     * Creates a new BlackHoleComponent with derived radii computed from mass.
     *
     * @param mass black hole mass in game-scale kilograms. Scale so that
     *             the resulting schwarzschildRadius is a visible gameplay size
     *             (50-500 game-units).
     * @return a fully initialised component
     */
    public static BlackHoleComponent create(float mass) {
        BlackHoleComponent bh = new BlackHoleComponent();
        bh.mass = mass;

        float rs = 2f * G * mass / C_SQUARED;
        bh.schwarzschildRadius = rs;
        bh.innerStableOrbit = 3f * rs;
        bh.photonSphere = 1.5f * rs;
        bh.accretionDiskInner = 3f * rs;
        bh.accretionDiskOuter = 20f * rs;
        bh.spin = 0f;
        bh.maxTimeDilation = 0.1f;
        bh.dilationFalloffRadius = 10f * rs;

        return bh;
    }

    @Override
    public void reset() {
        mass = 0f;
        schwarzschildRadius = 0f;
        innerStableOrbit = 0f;
        photonSphere = 0f;
        accretionDiskInner = 0f;
        accretionDiskOuter = 0f;
        spin = 0f;
        maxTimeDilation = 0.1f;
        dilationFalloffRadius = 0f;
    }
}
