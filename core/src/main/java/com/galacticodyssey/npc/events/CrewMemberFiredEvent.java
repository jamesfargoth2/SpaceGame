package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.FireReason;

public final class CrewMemberFiredEvent {
    public final Entity npc;
    public final FireReason reason;

    public CrewMemberFiredEvent(Entity npc, FireReason reason) {
        this.npc = npc;
        this.reason = reason;
    }
}
