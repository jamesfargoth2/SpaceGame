package com.galacticodyssey.core.blackhole.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when an entity crosses a black hole's event horizon
 * (r <= schwarzschildRadius). This is an irreversible destruction event;
 * the entity must be removed from the engine.
 */
public final class EventHorizonCrossedEvent {

    /** The entity that crossed the event horizon. */
    public final Entity entity;

    public EventHorizonCrossedEvent(Entity entity) {
        this.entity = entity;
    }
}
