package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormEnteredEvent {
    public final Entity stormEntity;
    public final float intensity;

    public StormEnteredEvent(Entity stormEntity, float intensity) {
        this.stormEntity = stormEntity;
        this.intensity = intensity;
    }
}
