package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.MoraleState;

public final class MoraleChangedEvent {
    public final Entity npc;
    public final MoraleState oldState;
    public final MoraleState newState;

    public MoraleChangedEvent(Entity npc, MoraleState oldState, MoraleState newState) {
        this.npc = npc;
        this.oldState = oldState;
        this.newState = newState;
    }
}
