package com.galacticodyssey.ship.lifesupport.events;

import com.badlogic.ashley.core.Entity;

public final class FireStatusEvent {
    public final Entity entity;
    public final boolean extinguished;

    public FireStatusEvent(Entity entity, boolean extinguished) {
        this.entity = entity;
        this.extinguished = extinguished;
    }
}
