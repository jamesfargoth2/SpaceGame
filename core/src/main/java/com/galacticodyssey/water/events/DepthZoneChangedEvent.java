package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.water.DepthZone;

public final class DepthZoneChangedEvent {
    public final Entity entity;
    public final DepthZone previousZone;
    public final DepthZone newZone;
    public final float depth;

    public DepthZoneChangedEvent(Entity entity, DepthZone previousZone,
                                  DepthZone newZone, float depth) {
        this.entity = entity;
        this.previousZone = previousZone;
        this.newZone = newZone;
        this.depth = depth;
    }
}
