package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerSurfacedEvent {
    public final Entity player;

    public PlayerSurfacedEvent(Entity player) {
        this.player = player;
    }
}
