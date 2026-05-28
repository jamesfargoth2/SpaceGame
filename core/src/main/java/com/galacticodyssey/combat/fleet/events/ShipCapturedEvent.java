package com.galacticodyssey.combat.fleet.events;

import com.badlogic.ashley.core.Entity;

public final class ShipCapturedEvent {
    public final Entity ship;
    public final Entity captor;
    public final String oldFactionId;

    public ShipCapturedEvent(Entity ship, Entity captor, String oldFactionId) {
        this.ship = ship;
        this.captor = captor;
        this.oldFactionId = oldFactionId;
    }
}
