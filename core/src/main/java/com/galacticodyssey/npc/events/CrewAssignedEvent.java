package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CrewAssignedEvent {
    public final Entity npc;
    public final Entity station;

    public CrewAssignedEvent(Entity npc, Entity station) {
        this.npc = npc;
        this.station = station;
    }
}
