package com.galacticodyssey.core.events;

public final class DialogueChoiceMadeEvent {
    public final String npcId;
    public final String choiceKey;

    public DialogueChoiceMadeEvent(String npcId, String choiceKey) {
        this.npcId = npcId;
        this.choiceKey = choiceKey;
    }
}
