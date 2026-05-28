package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.components.StatType;

public final class StatRevealedEvent {
    public final Entity npcEntity;
    public final StatType stat;
    public final float value;

    public StatRevealedEvent(Entity npcEntity, StatType stat, float value) {
        this.npcEntity = npcEntity;
        this.stat = stat;
        this.value = value;
    }
}
