package com.galacticodyssey.combat.fleet.events;

public final class FleetDestroyedEvent {
    public final String fleetId;
    public final String factionId;

    public FleetDestroyedEvent(String fleetId, String factionId) {
        this.fleetId = fleetId;
        this.factionId = factionId;
    }
}
