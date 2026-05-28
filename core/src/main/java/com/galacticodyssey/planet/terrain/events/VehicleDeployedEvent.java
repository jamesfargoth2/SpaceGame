package com.galacticodyssey.planet.terrain.events;

import com.badlogic.ashley.core.Entity;

public final class VehicleDeployedEvent {
    public final Entity vehicle;
    public final Entity ship;
    public VehicleDeployedEvent(Entity vehicle, Entity ship) {
        this.vehicle = vehicle; this.ship = ship;
    }
}
