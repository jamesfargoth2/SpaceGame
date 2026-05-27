package com.galacticodyssey.ship.structure.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.structure.ZoneId;

/** Published when a structural zone is so badly damaged that it detaches from the ship. */
public final class ComponentDetachEvent {
    public final Entity ship;
    public final ZoneId zone;

    public ComponentDetachEvent(Entity ship, ZoneId zone) {
        this.ship = ship;
        this.zone = zone;
    }
}
