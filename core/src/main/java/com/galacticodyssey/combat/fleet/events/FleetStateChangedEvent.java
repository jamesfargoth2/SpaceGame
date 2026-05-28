package com.galacticodyssey.combat.fleet.events;

import com.galacticodyssey.combat.fleet.data.FleetState;

public final class FleetStateChangedEvent {
    public final String fleetId;
    public final FleetState oldState;
    public final FleetState newState;

    public FleetStateChangedEvent(String fleetId, FleetState oldState, FleetState newState) {
        this.fleetId = fleetId;
        this.oldState = oldState;
        this.newState = newState;
    }
}
