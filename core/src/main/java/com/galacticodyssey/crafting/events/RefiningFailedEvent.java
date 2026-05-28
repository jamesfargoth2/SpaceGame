package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningFailureReason;

/**
 * Fired when a refining request is rejected.
 */
public final class RefiningFailedEvent {
    public final Entity entity;
    public final RefiningFailureReason reason;

    public RefiningFailedEvent(Entity entity, RefiningFailureReason reason) {
        this.entity = entity;
        this.reason = reason;
    }
}
