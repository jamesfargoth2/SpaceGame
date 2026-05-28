package com.galacticodyssey.core.events;

public final class NpcDialogueEvent {
    public final String npcId;
    public final String topic;

    public NpcDialogueEvent(String npcId, String topic) {
        this.npcId = npcId;
        this.topic = topic;
    }
}
