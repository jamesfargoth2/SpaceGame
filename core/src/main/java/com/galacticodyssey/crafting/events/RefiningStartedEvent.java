package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

/**
 * Fired when a refining job transitions from QUEUED to ACTIVE.
 */
public final class RefiningStartedEvent {
    public final Entity entity;
    public final RefiningJob job;

    public RefiningStartedEvent(Entity entity, RefiningJob job) {
        this.entity = entity;
        this.job = job;
    }
}
