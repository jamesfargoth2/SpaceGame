package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.CrewRole;

public final class CrewMemberHiredEvent {
    public final Entity npc;
    public final CrewRole role;

    public CrewMemberHiredEvent(Entity npc, CrewRole role) {
        this.npc = npc;
        this.role = role;
    }
}
