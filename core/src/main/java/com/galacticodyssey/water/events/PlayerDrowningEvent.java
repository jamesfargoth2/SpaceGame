package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerDrowningEvent {
    public final Entity player;

    public PlayerDrowningEvent(Entity player) {
        this.player = player;
    }
}
