package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CrewDesertedEvent {
    public final Entity npc;
    public final String locationId;

    public CrewDesertedEvent(Entity npc, String locationId) {
        this.npc = npc;
        this.locationId = locationId;
    }
}
