package com.galacticodyssey.npc.events;

import com.galacticodyssey.npc.data.DialogNode;

public final class DialogOpenedEvent {
    public final String npcId;
    public final String npcName;
    public final DialogNode node;

    public DialogOpenedEvent(String npcId, String npcName, DialogNode node) {
        this.npcId = npcId;
        this.npcName = npcName;
        this.node = node;
    }
}
