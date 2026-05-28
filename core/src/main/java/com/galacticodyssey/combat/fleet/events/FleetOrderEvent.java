package com.galacticodyssey.combat.fleet.events;

import com.galacticodyssey.combat.fleet.data.FleetOrder;

public final class FleetOrderEvent {
    public final String fleetId;
    public final FleetOrder order;

    public FleetOrderEvent(String fleetId, FleetOrder order) {
        this.fleetId = fleetId;
        this.order = order;
    }
}
