package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;

public final class ShipOverheatEvent {
    public final Entity shipEntity;
    public final String hardpointId;

    public ShipOverheatEvent(Entity shipEntity, String hardpointId) {
        this.shipEntity = shipEntity;
        this.hardpointId = hardpointId;
    }
}
