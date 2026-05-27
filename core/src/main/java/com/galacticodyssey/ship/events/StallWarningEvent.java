package com.galacticodyssey.ship.events;

import com.badlogic.ashley.core.Entity;

public final class StallWarningEvent {
    public final Entity ship;

    public StallWarningEvent(Entity ship) {
        this.ship = ship;
    }
}
