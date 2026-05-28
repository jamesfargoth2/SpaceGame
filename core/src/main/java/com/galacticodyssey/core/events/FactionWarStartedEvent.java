package com.galacticodyssey.core.events;

public final class FactionWarStartedEvent {
    public final String warId;
    public final String factionA;
    public final String factionB;
    public final String sectorId;

    public FactionWarStartedEvent(String warId, String factionA, String factionB, String sectorId) {
        this.warId = warId;
        this.factionA = factionA;
        this.factionB = factionB;
        this.sectorId = sectorId;
    }
}
