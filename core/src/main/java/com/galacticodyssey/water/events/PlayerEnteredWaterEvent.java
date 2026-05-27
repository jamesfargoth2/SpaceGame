package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerEnteredWaterEvent {
    public final Entity player;
    public final Entity waterBody;

    public PlayerEnteredWaterEvent(Entity player, Entity waterBody) {
        this.player = player;
        this.waterBody = waterBody;
    }
}
