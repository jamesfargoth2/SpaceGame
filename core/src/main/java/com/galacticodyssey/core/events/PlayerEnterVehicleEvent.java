package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerEnterVehicleEvent {
    public final Entity player;
    public final Entity vehicle;
    public PlayerEnterVehicleEvent(Entity player, Entity vehicle) {
        this.player = player; this.vehicle = vehicle;
    }
}
