package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackFailedEvent {
    public final Entity player;
    public final Entity target;

    public HackFailedEvent(Entity player, Entity target) {
        this.player = player;
        this.target = target;
    }
}
