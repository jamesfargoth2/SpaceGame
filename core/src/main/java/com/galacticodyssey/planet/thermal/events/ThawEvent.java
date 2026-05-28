package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class ThawEvent {
    public final Entity entity;
    public ThawEvent(Entity entity) { this.entity = entity; }
}
