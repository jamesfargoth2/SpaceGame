package com.galacticodyssey.ship.flooding;

/**
 * Describes a passage (doorway, hatch, vent) between two compartments
 * through which water can cross-flow during flooding.
 *
 * <p>The flow rate through a doorway is computed via an orifice-flow
 * model: {@code Q = Cd * A * sqrt(2 * g * |dh|)} where {@code dh} is
 * the head difference between the two compartments.
 */
public final class DoorwayConnection {

    /** ID of the first compartment. */
    public final String compartmentA;

    /** ID of the second compartment. */
    public final String compartmentB;

    /**
     * Cross-sectional area of the passage in m^2. A standard interior
     * doorway is roughly 0.8m wide x 2.0m tall = 1.6 m^2. Hatches and
     * vents can be smaller.
     */
    public final float passageArea;

    /**
     * Discharge coefficient for the orifice. Typical values:
     * <ul>
     *   <li>0.6 for a sharp-edged orifice (hull breach)</li>
     *   <li>0.4 for a partially obstructed doorway</li>
     *   <li>0.8 for a wide-open hatch</li>
     * </ul>
     */
    public final float dischargeCoefficient;

    /**
     * Whether this doorway can be sealed (e.g. a watertight hatch).
     * When sealed, no cross-flow occurs regardless of head difference.
     */
    public boolean sealable;

    /** Whether this doorway is currently sealed shut. */
    public boolean sealed;

    public DoorwayConnection(String compartmentA, String compartmentB,
                             float passageArea, float dischargeCoefficient) {
        this.compartmentA = compartmentA;
        this.compartmentB = compartmentB;
        this.passageArea = passageArea;
        this.dischargeCoefficient = dischargeCoefficient;
    }
}
