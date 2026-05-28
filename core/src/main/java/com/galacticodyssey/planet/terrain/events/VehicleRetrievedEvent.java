package com.galacticodyssey.planet.terrain.events;

import com.badlogic.ashley.core.Entity;

public final class VehicleRetrievedEvent {
    public final String vehicleDefinitionId;
    public final Entity ship;
    public VehicleRetrievedEvent(String vehicleDefinitionId, Entity ship) {
        this.vehicleDefinitionId = vehicleDefinitionId; this.ship = ship;
    }
}
