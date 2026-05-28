package com.galacticodyssey.core.events;

public final class ShipMissingEvent {
    public final String shipId;
    public final String lastKnownLocationId;

    public ShipMissingEvent(String shipId, String lastKnownLocationId) {
        this.shipId = shipId;
        this.lastKnownLocationId = lastKnownLocationId;
    }
}
