package com.galacticodyssey.core.events;

public class CargoDeliveredEvent {
    public final String cargoType;
    public final String destinationId;

    public CargoDeliveredEvent(String cargoType, String destinationId) {
        this.cargoType = cargoType;
        this.destinationId = destinationId;
    }
}
