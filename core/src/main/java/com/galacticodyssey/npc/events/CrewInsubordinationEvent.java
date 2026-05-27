package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.NpcGoal;

public final class CrewInsubordinationEvent {
    public final Entity npc;
    public final NpcGoal refusedGoal;

    public CrewInsubordinationEvent(Entity npc, NpcGoal refusedGoal) {
        this.npc = npc;
        this.refusedGoal = refusedGoal;
    }
}
