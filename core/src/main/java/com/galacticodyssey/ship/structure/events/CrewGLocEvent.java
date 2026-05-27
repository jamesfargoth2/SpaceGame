package com.galacticodyssey.ship.structure.events;

import com.badlogic.ashley.core.Entity;

/** Published when an entity's accumulated G-fatigue exceeds 1 and triggers G-LOC. */
public final class CrewGLocEvent {
    public final Entity entity;
    public final float gForce;

    public CrewGLocEvent(Entity entity, float gForce) {
        this.entity = entity;
        this.gForce = gForce;
    }
}
