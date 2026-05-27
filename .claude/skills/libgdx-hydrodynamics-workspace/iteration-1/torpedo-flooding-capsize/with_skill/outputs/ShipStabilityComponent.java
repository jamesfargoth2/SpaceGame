package com.galacticodyssey.ship.flooding.components;

import com.badlogic.ashley.core.Component;

/**
 * Ashley component that tracks a ship's hydrostatic stability parameters.
 *
 * <p>Optional companion to {@link FloodingComponent}. Provides extended
 * hydrostatic stability metadata (metacentric height, righting arm,
 * capsize thresholds) that can be used by advanced stability systems
 * or physics extensions beyond the core flooding simulation.</p>
 */
public class ShipStabilityComponent implements Component {

    /** Metacentric height GM in metres. Higher = more stable. */
    public float metacentricHeight = 2.0f;

    /** Baseline GM when the ship is undamaged and unladen (m). */
    public float baselineGM = 2.0f;

    /** Righting moment GZ in metres at the current heel angle. */
    public float rightingArm;

    /**
     * Roll angle in degrees. Positive = starboard down.
     * Updated from the Bullet rigid body each tick.
     */
    public float rollDeg;

    /**
     * Pitch angle in degrees. Positive = bow down (trim by the bow).
     */
    public float pitchDeg;

    /** Ship's dry mass in kg (hull + cargo + crew, no flood water). */
    public float dryMass = 50_000f;

    /** Displacement volume in m^3 at current draft. */
    public float displacementVolume = 48.78f;

    /** Height of the centre of gravity above the keel (m). */
    public float centreOfGravityHeight = 3.0f;

    /** Height of the metacentre above the keel (m). */
    public float metacentreHeight = 5.0f;

    /** Waterplane area moment of inertia (m^4). */
    public float waterplaneInertia = 200f;

    /**
     * Whether the ship has entered an irrecoverable capsize.
     * Set when roll exceeds 60 degrees for a sustained period.
     */
    public boolean capsized;

    /** Capsize angle threshold in degrees. */
    public float capsizeAngle = 60f;

    /** Time in seconds the roll has exceeded the capsize angle. */
    public float capsizeTimer;

    /** Duration in seconds of sustained over-angle before capsize is declared. */
    public float capsizeDuration = 3.0f;

    /** Current stability warning level: 0=normal, 1=caution, 2=warning, 3=critical. */
    public int warningLevel;
}
