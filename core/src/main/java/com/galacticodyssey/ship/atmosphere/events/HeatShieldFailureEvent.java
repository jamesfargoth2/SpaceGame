package com.galacticodyssey.ship.atmosphere.events;

import com.badlogic.ashley.core.Entity;

/** Published when an entity's heat shield temperature exceeds its maximum. */
public final class HeatShieldFailureEvent {

    public final Entity entity;

    public HeatShieldFailureEvent(Entity entity) {
        this.entity = entity;
    }
}
