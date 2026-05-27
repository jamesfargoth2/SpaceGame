package com.galacticodyssey.water.data;

/**
 * Data-driven definition for a surface vessel type (boat, ship, freighter).
 * Loaded from JSON at runtime; never hardcode vessel stats.
 *
 * <p>Hull physics scale naturally with these parameters: longer hulls have
 * higher hull speeds (Froude scales with {@code sqrt(length)}), heavier
 * hulls roll more slowly, and wider beams increase lateral drag. The
 * {@link com.galacticodyssey.water.VesselFactory} computes derived values
 * (displacement volume, wetted area) from these primitives.
 */
public class VesselData {

    public String id = "";
    public String name = "";

    /** "small", "medium", or "large" — used for LOD and AI decisions. */
    public String sizeClass = "small";

    // -- Hull geometry (metres) --

    public float length = 12f;
    public float beam = 4f;
    /** Depth of hull below the waterline. */
    public float draft = 1.2f;
    /** Height of hull above the waterline. */
    public float freeboard = 1.0f;

    // -- Mass --

    /** Dry mass in kg (hull + machinery, no cargo). */
    public float dryMass = 5000f;

    // -- Hull form --

    /**
     * Block coefficient: ratio of actual displaced volume to the bounding
     * box (length * beam * draft). Fine hulls ~0.4, full cargo hulls ~0.8.
     */
    public float blockCoefficient = 0.6f;

    // -- Drag coefficients --

    /** Viscous (skin friction) drag coefficient for streamlined direction. */
    public float skinFrictionCd = 0.05f;
    /** Form (pressure) drag coefficient for broadside/bluff-body resistance. */
    public float formDragCd = 0.8f;

    // -- Motor --

    /** Maximum forward thrust in Newtons. */
    public float maxThrust = 8000f;
    /** Maximum reverse thrust in Newtons. */
    public float maxReverseThrust = 3000f;
    /** Rudder torque in Newton-metres. */
    public float rudderTorque = 25000f;
    /** Throttle smoothing rate (higher = snappier). */
    public float throttleResponseRate = 2.0f;
    /** Rudder smoothing rate (higher = snappier). */
    public float rudderResponseRate = 1.5f;
    /** Forward speed (m/s) needed for full rudder effectiveness. */
    public float minSpeedForFullRudder = 3.0f;

    // -- Buoyancy --

    /** Number of hull sample points for buoyancy/drag. More = smoother response. */
    public int samplePointCount = 16;

    // -- Bullet physics tuning --

    /** Linear damping (very low — HydrodynamicDragSystem handles drag). */
    public float linearDamping = 0.02f;
    /** Angular damping (very low — water drag handles rotation decay). */
    public float angularDamping = 0.05f;
}
