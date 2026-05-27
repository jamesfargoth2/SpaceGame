package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormIntensifiedEvent {
    public final Entity stormEntity;
    public final float newIntensity;

    public StormIntensifiedEvent(Entity stormEntity, float newIntensity) {
        this.stormEntity = stormEntity;
        this.newIntensity = newIntensity;
    }
}
