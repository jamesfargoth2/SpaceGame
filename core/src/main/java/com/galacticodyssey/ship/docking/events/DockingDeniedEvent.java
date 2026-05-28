package com.galacticodyssey.ship.docking.events;

public final class DockingDeniedEvent {
    public final String stationId;
    public final String factionId;
    public final String reason;

    public DockingDeniedEvent(String stationId, String factionId, String reason) {
        this.stationId = stationId;
        this.factionId = factionId;
        this.reason = reason;
    }
}
