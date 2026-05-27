package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class ShipSinkingEvent {
    public final Entity shipEntity;
    public final float totalFloodedMass;

    public ShipSinkingEvent(Entity shipEntity, float totalFloodedMass) {
        this.shipEntity = shipEntity;
        this.totalFloodedMass = totalFloodedMass;
    }
}
