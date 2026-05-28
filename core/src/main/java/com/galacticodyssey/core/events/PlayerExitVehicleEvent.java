package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerExitVehicleEvent {
    public final Entity player;
    public final Entity vehicle;
    public PlayerExitVehicleEvent(Entity player, Entity vehicle) {
        this.player = player; this.vehicle = vehicle;
    }
}
