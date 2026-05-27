package com.galacticodyssey.ship.flooding.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a new hull breach is created in a compartment, typically
 * from torpedo impact, collision damage, or exceeding structural limits.
 *
 * <p>The flooding system subscribes to this event to initiate water
 * ingress in the affected compartment. Audio/VFX systems can use it
 * to trigger breach sound effects, sparks, and decompression visuals.
 */
public final class HullBreachEvent {

    /** The ship entity that sustained the breach. */
    public final Entity shipEntity;

    /** ID of the compartment where the breach occurred. */
    public final String compartmentId;

    /** Area of the breach opening in m^2. Larger breaches flood faster. */
    public final float breachArea;

    /**
     * Depth of the breach below the external waterline/pressure
     * boundary in metres. Drives the Torricelli flow rate.
     */
    public final float breachDepth;

    public HullBreachEvent(Entity shipEntity, String compartmentId,
                           float breachArea, float breachDepth) {
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
        this.breachArea = breachArea;
        this.breachDepth = breachDepth;
    }
}
