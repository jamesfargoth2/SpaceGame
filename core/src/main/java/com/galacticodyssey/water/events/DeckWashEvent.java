package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class DeckWashEvent {
    public final Entity shipEntity;
    public final float flowRate;

    public DeckWashEvent(Entity shipEntity, float flowRate) {
        this.shipEntity = shipEntity;
        this.flowRate = flowRate;
    }
}
