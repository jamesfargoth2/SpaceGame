package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CandidateSelectedEvent {
    public final Entity npcEntity;

    public CandidateSelectedEvent(Entity npcEntity) {
        this.npcEntity = npcEntity;
    }
}
