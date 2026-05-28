package com.galacticodyssey.combat.fleet.events;

import com.badlogic.ashley.core.Entity;

public final class ShipDisabledEvent {
    public final Entity ship;
    public final Entity attacker;

    public ShipDisabledEvent(Entity ship, Entity attacker) {
        this.ship = ship;
        this.attacker = attacker;
    }
}
