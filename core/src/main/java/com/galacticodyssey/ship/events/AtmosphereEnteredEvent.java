package com.galacticodyssey.ship.events;

import com.badlogic.ashley.core.Entity;

public final class AtmosphereEnteredEvent {
    public final Entity ship;
    public final Entity planet;

    public AtmosphereEnteredEvent(Entity ship, Entity planet) {
        this.ship = ship;
        this.planet = planet;
    }
}
