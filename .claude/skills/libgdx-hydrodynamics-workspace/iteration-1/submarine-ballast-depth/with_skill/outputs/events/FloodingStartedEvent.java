package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a compartment first begins taking on water through a
 * breach or from a connected flooded compartment.
 */
public final class FloodingStartedEvent {

    public final Entity entity;
    public final String compartmentId;

    public FloodingStartedEvent(Entity entity, String compartmentId) {
        this.entity = entity;
        this.compartmentId = compartmentId;
    }
}
