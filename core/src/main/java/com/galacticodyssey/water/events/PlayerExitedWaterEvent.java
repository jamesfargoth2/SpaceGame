package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerExitedWaterEvent {
    public final Entity player;

    public PlayerExitedWaterEvent(Entity player) {
        this.player = player;
    }
}
