package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerBailingEvent {
    public final Entity player;
    public final String compartmentId;
    public final float volumeRemoved;

    public PlayerBailingEvent(Entity player, String compartmentId, float volumeRemoved) {
        this.player = player;
        this.compartmentId = compartmentId;
        this.volumeRemoved = volumeRemoved;
    }
}
