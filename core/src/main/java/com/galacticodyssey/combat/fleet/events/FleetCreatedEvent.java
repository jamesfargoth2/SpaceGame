package com.galacticodyssey.combat.fleet.events;

public final class FleetCreatedEvent {
    public final String fleetId;
    public final String factionId;
    public final double x, y, z;

    public FleetCreatedEvent(String fleetId, String factionId, double x, double y, double z) {
        this.fleetId = fleetId;
        this.factionId = factionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
