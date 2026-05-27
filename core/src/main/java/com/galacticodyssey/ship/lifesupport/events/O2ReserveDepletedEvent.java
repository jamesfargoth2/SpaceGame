package com.galacticodyssey.ship.lifesupport.events;

import com.badlogic.ashley.core.Entity;

public final class O2ReserveDepletedEvent {
    public final Entity entity;

    public O2ReserveDepletedEvent(Entity entity) {
        this.entity = entity;
    }
}
