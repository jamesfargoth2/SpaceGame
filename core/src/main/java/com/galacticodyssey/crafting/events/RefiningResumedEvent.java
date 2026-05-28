package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

/**
 * Fired when a paused refining job is resumed.
 */
public final class RefiningResumedEvent {
    public final Entity entity;
    public final RefiningJob job;

    public RefiningResumedEvent(Entity entity, RefiningJob job) {
        this.entity = entity;
        this.job = job;
    }
}
