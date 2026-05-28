package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public final class SOIChangedEvent {
    public final Entity entity;
    public final Entity oldDominantBody;
    public final Entity newDominantBody;

    public SOIChangedEvent(Entity entity, Entity oldDominantBody, Entity newDominantBody) {
        this.entity = entity;
        this.oldDominantBody = oldDominantBody;
        this.newDominantBody = newDominantBody;
    }
}
