package com.galacticodyssey.ship.atmosphere;

import com.badlogic.ashley.core.Component;

/**
 * Tracks the instantaneous aerodynamic state of an entity in atmosphere.
 * Updated each frame by {@link AeroForceSystem} and {@link EntryHeatSystem}.
 */
public class AeroStateComponent implements Component {

    // --- aerodynamic state ---
    /** Current Mach number. */
    public float mach;

    /** Current angle of attack in radians. */
    public float angleOfAttack;

    /** Magnitude of the lift force in Newtons. */
    public float currentLift;

    /** Magnitude of the drag force in Newtons. */
    public float currentDrag;

    /** True when the angle of attack exceeds the stall angle. */
    public boolean isStalled;

    /** Current flight regime derived from Mach number. */
    public FlightRegime flightRegime = FlightRegime.SUBSONIC;

    // --- thermal state ---
    /** Current heat-shield temperature in Kelvin. */
    public float heatShieldTemp = 300f;

    /** Maximum temperature the heat shield can withstand (K). */
    public float heatShieldMaxTemp = 2500f;

    /** Heat-shield mass in kg (for thermal inertia). */
    public float heatShieldMass = 200f;

    /** Specific heat capacity of the heat shield in J/(kg*K). */
    public float heatShieldCp = 1000f;

    /**
     * Flight regime classification based on Mach number.
     */
    public enum FlightRegime {
        SUBSONIC,
        TRANSONIC,
        SUPERSONIC,
        HYPERSONIC;

        /** Determine the flight regime for the given Mach number. */
        public static FlightRegime fromMach(float mach) {
            if (mach < 0.8f) return SUBSONIC;
            if (mach < 1.2f) return TRANSONIC;
            if (mach < 5.0f) return SUPERSONIC;
            return HYPERSONIC;
        }
    }
}
