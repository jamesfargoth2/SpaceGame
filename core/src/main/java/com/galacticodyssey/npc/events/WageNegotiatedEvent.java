package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class WageNegotiatedEvent {
    public final Entity npcEntity;
    public final float finalWage;
    public final float discountPercent;

    public WageNegotiatedEvent(Entity npcEntity, float finalWage, float discountPercent) {
        this.npcEntity = npcEntity;
        this.finalWage = finalWage;
        this.discountPercent = discountPercent;
    }
}
