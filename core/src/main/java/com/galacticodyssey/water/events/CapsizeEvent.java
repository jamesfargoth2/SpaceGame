package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class CapsizeEvent {
    public final Entity entity;
    public final float currentRollDeg;
    public final float totalFloodedMass;

    public CapsizeEvent(Entity entity, float currentRollDeg, float totalFloodedMass) {
        this.entity = entity;
        this.currentRollDeg = currentRollDeg;
        this.totalFloodedMass = totalFloodedMass;
    }
}
