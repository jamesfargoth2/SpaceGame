package com.galacticodyssey.ship.atmosphere.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when an entity crosses the atmosphere boundary of a planet.
 * {@code entering} is true when entering the atmosphere, false when leaving.
 */
public final class AtmosphereTransitionEvent {

    public final Entity entity;
    public final boolean entering;

    public AtmosphereTransitionEvent(Entity entity, boolean entering) {
        this.entity = entity;
        this.entering = entering;
    }
}
