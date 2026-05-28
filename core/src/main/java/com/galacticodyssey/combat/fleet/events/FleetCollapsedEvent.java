package com.galacticodyssey.combat.fleet.events;

public final class FleetCollapsedEvent {
    public final String fleetId;

    public FleetCollapsedEvent(String fleetId) {
        this.fleetId = fleetId;
    }
}
