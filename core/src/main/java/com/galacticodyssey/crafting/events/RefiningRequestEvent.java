package com.galacticodyssey.crafting.events;

import com.badlogic.ashley.core.Entity;

/**
 * Fired when a player requests a refining operation to begin.
 */
public final class RefiningRequestEvent {
    public final Entity entity;
    public final String recipeId;
    public final int batchCount;

    public RefiningRequestEvent(Entity entity, String recipeId, int batchCount) {
        this.entity = entity;
        this.recipeId = recipeId;
        this.batchCount = batchCount;
    }

    public RefiningRequestEvent(Entity entity, String recipeId) {
        this(entity, recipeId, 1);
    }
}
