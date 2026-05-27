package com.galacticodyssey.ship.lifesupport.events;

import com.badlogic.ashley.core.Entity;

public final class ScrubberSaturatedEvent {
    public final Entity entity;

    public ScrubberSaturatedEvent(Entity entity) {
        this.entity = entity;
    }
}
