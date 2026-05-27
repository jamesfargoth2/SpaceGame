package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerStartedDivingEvent {
    public final Entity player;
    public final float depth;

    public PlayerStartedDivingEvent(Entity player, float depth) {
        this.player = player;
        this.depth = depth;
    }
}
