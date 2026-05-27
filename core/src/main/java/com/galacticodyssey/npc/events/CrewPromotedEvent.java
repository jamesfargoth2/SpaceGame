package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.CrewRank;

public final class CrewPromotedEvent {
    public final Entity npc;
    public final CrewRank oldRank;
    public final CrewRank newRank;

    public CrewPromotedEvent(Entity npc, CrewRank oldRank, CrewRank newRank) {
        this.npc = npc;
        this.oldRank = oldRank;
        this.newRank = newRank;
    }
}
