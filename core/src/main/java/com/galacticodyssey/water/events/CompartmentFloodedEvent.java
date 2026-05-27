package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Fired when a compartment becomes fully flooded with water.
 * UI/audio/VFX systems can subscribe to trigger alarms, sounds, etc.
 */
public final class CompartmentFloodedEvent {

    /** The submarine entity. */
    public final Entity submarine;

    /** The compartment entity that is now fully flooded. */
    public final Entity compartment;

    /** The compartment identifier. */
    public final String compartmentId;

    /** Total number of flooded compartments on the submarine. */
    public final int totalFloodedCount;

    /** Total number of compartments on the submarine. */
    public final int totalCompartmentCount;

    public CompartmentFloodedEvent(Entity submarine, Entity compartment, String compartmentId,
                                   int totalFloodedCount, int totalCompartmentCount) {
        this.submarine = submarine;
        this.compartment = compartment;
        this.compartmentId = compartmentId;
        this.totalFloodedCount = totalFloodedCount;
        this.totalCompartmentCount = totalCompartmentCount;
    }
}
