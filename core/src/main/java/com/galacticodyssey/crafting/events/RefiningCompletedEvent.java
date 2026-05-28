package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.RefiningJob;

import java.util.Map;

/**
 * Fired when a refining job reaches full progress and produces output materials.
 */
public final class RefiningCompletedEvent {
    public final Entity entity;
    public final RefiningJob job;
    public final Map<String, Integer> producedMaterials;

    public RefiningCompletedEvent(Entity entity, RefiningJob job, Map<String, Integer> producedMaterials) {
        this.entity = entity;
        this.job = job;
        this.producedMaterials = producedMaterials;
    }
}
