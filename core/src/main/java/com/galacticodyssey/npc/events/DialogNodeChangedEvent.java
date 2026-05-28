package com.galacticodyssey.npc.events;

import com.galacticodyssey.npc.data.DialogNode;

public final class DialogNodeChangedEvent {
    public final String npcId;
    public final String npcName;
    public final DialogNode node;

    public DialogNodeChangedEvent(String npcId, String npcName, DialogNode node) {
        this.npcId = npcId;
        this.npcName = npcName;
        this.node = node;
    }
}
