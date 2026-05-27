package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;

/**
 * Defines the structural properties of a submarine hull.
 * Crush depth, displacement volume, and hull integrity are stored here.
 * Values should be populated from data files at runtime.
 */
public class SubmarineHullComponent implements Component {

    /** Maximum depth (meters) before hull starts taking damage. */
    public float crushDepth = 500f;

    /** Warning threshold as a fraction of crush depth (0-1). */
    public float depthWarningFraction = 0.85f;

    /** Displacement volume in cubic meters when fully surfaced. */
    public float displacementVolume = 120f;

    /** Hull structural integrity (0-1). 1 = pristine, 0 = destroyed. */
    public float integrity = 1f;

    /** Rate of integrity loss per second when below crush depth, scaled by overshoot. */
    public float crushDamageRate = 0.05f;

    /** Whether the hull has been breached (water can enter). */
    public boolean breached = false;

    /** Number of active breach points. */
    public int breachCount = 0;

    /** Dry mass of the submarine in kg. */
    public float dryMass = 50000f;

    /** Length of the submarine along its forward axis (meters). */
    public float length = 25f;

    /** Beam (width) of the submarine (meters). */
    public float beam = 6f;

    /** Height (draft) of the submarine (meters). */
    public float height = 5f;
}
