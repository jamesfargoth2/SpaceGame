package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class HullRepairEvent {
    public final Entity player;
    public final Entity shipEntity;
    public final String compartmentId;

    public HullRepairEvent(Entity player, Entity shipEntity, String compartmentId) {
        this.player = player;
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
    }
}
