package com.galacticodyssey.ship.fluid;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

/**
 * Lumped-mass pendulum model of fluid inside a spherical tank.
 * The fluid centre-of-mass can shift within the tank, producing
 * slosh forces and torques on the parent ship.
 */
public class SloshTankComponent implements Component, Pool.Poolable {

    /** Local position of the tank centre in ship space. */
    public final Vector3 tankCentre = new Vector3();

    /** Tank radius in metres -- sets the slosh amplitude clamp. */
    public float tankRadius = 1f;

    /** Internal volume of the tank in cubic metres. */
    public float volume = 1f;

    /** Current fluid mass in kg. */
    public float fluidMass = 0f;

    /** Fluid density in kg/m^3 (used to derive fill fraction). */
    public float density = 1000f;

    /** Offset of fluid centre-of-mass from tank centre (local). */
    public final Vector3 fluidLocalPos = new Vector3();

    /** Velocity of the slosh mass relative to the tank (local). */
    public final Vector3 fluidVelocity = new Vector3();

    /** Slosh natural frequency in rad/s -- updated each tick. */
    public float naturalFrequency = 0f;

    /** Damping coefficient for the slosh oscillator. */
    public float damping = 0.1f;

    /** Whether anti-slosh baffles are installed. */
    public boolean hasBaffles = false;

    /** Baffle suppression efficiency in [0, 1]. */
    public float baffleEfficiency = 0f;

    /**
     * Returns the ratio of current fluid volume to total tank volume.
     * A value of 0 means empty; 1 means full.
     */
    public float fillFraction() {
        float capacity = density * volume;
        if (capacity <= 0f) return 0f;
        return fluidMass / capacity;
    }

    @Override
    public void reset() {
        tankCentre.setZero();
        tankRadius = 1f;
        volume = 1f;
        fluidMass = 0f;
        density = 1000f;
        fluidLocalPos.setZero();
        fluidVelocity.setZero();
        naturalFrequency = 0f;
        damping = 0.1f;
        hasBaffles = false;
        baffleEfficiency = 0f;
    }
}
