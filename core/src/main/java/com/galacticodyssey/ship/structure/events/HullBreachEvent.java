package com.galacticodyssey.ship.structure.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.structure.ZoneId;

/** Published when a structural zone's integrity reaches zero and the hull breaches. */
public final class HullBreachEvent {
    public final Entity entity;
    public final ZoneId zone;

    public HullBreachEvent(Entity entity, ZoneId zone) {
        this.entity = entity;
        this.zone = zone;
    }
}
