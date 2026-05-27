package com.galacticodyssey.ship.events;

import com.badlogic.ashley.core.Entity;

public final class ReentryHeatingEvent {
    public final Entity ship;
    public final float heatLevel;

    public ReentryHeatingEvent(Entity ship, float heatLevel) {
        this.ship = ship;
        this.heatLevel = heatLevel;
    }
}
