package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

/**
 * Fired when a refining job is paused.
 */
public final class RefiningPausedEvent {
    public final Entity entity;
    public final RefiningJob job;

    public RefiningPausedEvent(Entity entity, RefiningJob job) {
        this.entity = entity;
        this.job = job;
    }
}
