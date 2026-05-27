package com.galacticodyssey.ship.events;

import com.badlogic.ashley.core.Entity;

public final class FuelDepletedEvent {

    public final Entity shipEntity;

    public FuelDepletedEvent(Entity shipEntity) {
        this.shipEntity = shipEntity;
    }
}
