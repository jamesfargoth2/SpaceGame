package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackSucceededEvent {
    public final Entity player;
    public final Entity target;
    public final Object effect; // replaced with HackEffect in Task 2

    public HackSucceededEvent(Entity player, Entity target, Object effect) {
        this.player = player;
        this.target = target;
        this.effect = effect;
    }
}
