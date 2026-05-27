package com.galacticodyssey.ship.thermal.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a ship manually vents its heat sinks, producing a visible
 * thermal plume and a brief hull temperature spike.
 */
public final class HeatVentEvent {
    public final Entity entity;
    public final float ventedFraction;

    public HeatVentEvent(Entity entity, float ventedFraction) {
        this.entity = entity;
        this.ventedFraction = ventedFraction;
    }
}
