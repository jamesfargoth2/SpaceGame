package com.galacticodyssey.core.events;

public class ReputationChangeEvent {
    public final String factionId;
    public final float delta;
    public final String sourceId;

    public ReputationChangeEvent(String factionId, float delta, String sourceId) {
        this.factionId = factionId;
        this.delta = delta;
        this.sourceId = sourceId;
    }
}
