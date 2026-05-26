package com.galacticodyssey.economy.events;

public final class CargoChangedEvent {
    public final int shipEntityId;

    public CargoChangedEvent(int shipEntityId) {
        this.shipEntityId = shipEntityId;
    }
}
