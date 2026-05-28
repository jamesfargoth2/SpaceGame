package com.galacticodyssey.combat.fleet.events;

import com.galacticodyssey.combat.fleet.data.FleetDoctrine;

public final class FleetDoctrineChangedEvent {
    public final String fleetId;
    public final FleetDoctrine oldDoctrine;
    public final FleetDoctrine newDoctrine;

    public FleetDoctrineChangedEvent(String fleetId, FleetDoctrine oldDoctrine, FleetDoctrine newDoctrine) {
        this.fleetId = fleetId;
        this.oldDoctrine = oldDoctrine;
        this.newDoctrine = newDoctrine;
    }
}
