package com.galacticodyssey.ship.events;

import com.badlogic.ashley.core.Entity;

public final class AtmosphereExitedEvent {
    public final Entity ship;

    public AtmosphereExitedEvent(Entity ship) {
        this.ship = ship;
    }
}
