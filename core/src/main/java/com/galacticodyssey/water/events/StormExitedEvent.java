package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormExitedEvent {
    public final Entity stormEntity;

    public StormExitedEvent(Entity stormEntity) {
        this.stormEntity = stormEntity;
    }
}
