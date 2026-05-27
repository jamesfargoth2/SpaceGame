package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class FloodingStartedEvent {
    public final Entity entity;
    public final String compartmentId;
    public final boolean fromBreach;

    public FloodingStartedEvent(Entity entity, String compartmentId, boolean fromBreach) {
        this.entity = entity;
        this.compartmentId = compartmentId;
        this.fromBreach = fromBreach;
    }
}
