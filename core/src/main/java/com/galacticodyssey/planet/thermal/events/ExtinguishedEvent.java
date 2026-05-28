package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class ExtinguishedEvent {
    public final Entity entity;
    public ExtinguishedEvent(Entity entity) { this.entity = entity; }
}
