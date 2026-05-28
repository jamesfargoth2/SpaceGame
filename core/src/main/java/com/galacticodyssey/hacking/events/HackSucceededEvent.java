package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.hacking.HackEffect;

public final class HackSucceededEvent {
    public final Entity player;
    public final Entity target;
    public final HackEffect effect;

    public HackSucceededEvent(Entity player, Entity target, HackEffect effect) {
        this.player = player;
        this.target = target;
        this.effect = effect;
    }
}
