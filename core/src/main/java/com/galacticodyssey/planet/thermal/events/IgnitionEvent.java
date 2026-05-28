package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class IgnitionEvent {
    public final Entity entity;
    public IgnitionEvent(Entity entity) { this.entity = entity; }
}
