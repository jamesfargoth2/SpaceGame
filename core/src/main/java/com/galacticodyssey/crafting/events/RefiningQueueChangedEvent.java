package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

import java.util.List;

/**
 * Fired when the refinery queue changes (job added, removed, or reordered).
 */
public final class RefiningQueueChangedEvent {
    public final Entity entity;
    public final List<RefiningJob> queue;

    public RefiningQueueChangedEvent(Entity entity, List<RefiningJob> queue) {
        this.entity = entity;
        this.queue = List.copyOf(queue);
    }
}
