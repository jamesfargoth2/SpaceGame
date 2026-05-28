package com.galacticodyssey.npc.events;

public final class DialogClosedEvent {
    public final String npcId;

    public DialogClosedEvent(String npcId) {
        this.npcId = npcId;
    }
}
