package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** A ship has become boardable (engines disabled → VULNERABLE phase). */
public final class ShipBoardableEvent {
    public final Entity ship;
    public final Entity aggressor;

    public ShipBoardableEvent(Entity ship, Entity aggressor) {
        this.ship = ship;
        this.aggressor = aggressor;
    }
}
