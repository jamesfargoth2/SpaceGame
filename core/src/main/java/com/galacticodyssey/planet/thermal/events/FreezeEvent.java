package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class FreezeEvent {
    public final Entity entity;
    public FreezeEvent(Entity entity) { this.entity = entity; }
}
