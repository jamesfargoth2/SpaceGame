package com.galacticodyssey.core.events;

public class CargoShipAttackedEvent {
    public final String attackerId;
    public final String cargoType;
    public final String locationId;

    public CargoShipAttackedEvent(String attackerId, String cargoType, String locationId) {
        this.attackerId = attackerId;
        this.cargoType = cargoType;
        this.locationId = locationId;
    }
}
