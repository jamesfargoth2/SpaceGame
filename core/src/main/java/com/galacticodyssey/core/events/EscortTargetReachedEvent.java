package com.galacticodyssey.core.events;

public class EscortTargetReachedEvent {
    public final String targetId;

    public EscortTargetReachedEvent(String targetId) {
        this.targetId = targetId;
    }
}
