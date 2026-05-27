package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Fired when the submarine hull breaches due to exceeding crush depth
 * or taking external damage. Listeners (audio, VFX, UI) subscribe to react.
 */
public final class HullBreachEvent {

    /** The submarine entity whose hull breached. */
    public final Entity submarine;

    /** The compartment entity where the breach occurred. */
    public final Entity compartment;

    /** The compartment identifier. */
    public final String compartmentId;

    /** Current depth at time of breach (meters). */
    public final float depth;

    /** Current hull integrity (0-1). */
    public final float hullIntegrity;

    public HullBreachEvent(Entity submarine, Entity compartment, String compartmentId,
                           float depth, float hullIntegrity) {
        this.submarine = submarine;
        this.compartment = compartment;
        this.compartmentId = compartmentId;
        this.depth = depth;
        this.hullIntegrity = hullIntegrity;
    }
}
