package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class DeckAwashEvent {
    public final Entity shipEntity;

    public DeckAwashEvent(Entity shipEntity) {
        this.shipEntity = shipEntity;
    }
}
