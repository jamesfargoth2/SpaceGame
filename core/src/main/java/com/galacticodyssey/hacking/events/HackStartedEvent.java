package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackStartedEvent {
    public final Entity player;
    public final Entity target;

    public HackStartedEvent(Entity player, Entity target) {
        this.player = player;
        this.target = target;
    }
}
