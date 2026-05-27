package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class BilgeAlarmEvent {
    public final Entity shipEntity;
    public final String compartmentId;
    public final float fillFraction;

    public BilgeAlarmEvent(Entity shipEntity, String compartmentId, float fillFraction) {
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
        this.fillFraction = fillFraction;
    }
}
