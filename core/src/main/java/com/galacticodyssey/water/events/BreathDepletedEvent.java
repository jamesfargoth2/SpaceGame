package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class BreathDepletedEvent {
    public final Entity player;

    public BreathDepletedEvent(Entity player) {
        this.player = player;
    }
}
