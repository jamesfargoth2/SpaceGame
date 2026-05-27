package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormApproachingEvent {
    public final Entity stormEntity;
    public final float distance;
    public final float bearing;

    public StormApproachingEvent(Entity stormEntity, float distance, float bearing) {
        this.stormEntity = stormEntity;
        this.distance = distance;
        this.bearing = bearing;
    }
}
