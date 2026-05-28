package com.galacticodyssey.combat.fleet.events;

public final class FactionFleetMusteredEvent {
    public final String factionId;
    public final String fleetId;

    public FactionFleetMusteredEvent(String factionId, String fleetId) {
        this.factionId = factionId;
        this.fleetId = fleetId;
    }
}
