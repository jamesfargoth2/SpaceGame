package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

import java.util.Map;

/**
 * Fired when a refining job is cancelled, returning partial inputs to the player.
 */
public final class RefiningCancelledEvent {
    public final Entity entity;
    public final RefiningJob job;
    public final Map<String, Integer> returnedInputs;

    public RefiningCancelledEvent(Entity entity, RefiningJob job, Map<String, Integer> returnedInputs) {
        this.entity = entity;
        this.job = job;
        this.returnedInputs = returnedInputs;
    }
}
