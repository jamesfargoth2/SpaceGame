package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class BreachSealedEvent {
    public final Entity entity;
    public final String compartmentId;

    public BreachSealedEvent(Entity entity, String compartmentId) {
        this.entity = entity;
        this.compartmentId = compartmentId;
    }
}
