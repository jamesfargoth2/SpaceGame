package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CrewInjuredEvent {
    public final Entity npc;
    public final float damage;

    public CrewInjuredEvent(Entity npc, float damage) {
        this.npc = npc;
        this.damage = damage;
    }
}
