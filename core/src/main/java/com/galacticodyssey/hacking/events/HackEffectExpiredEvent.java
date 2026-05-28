package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackEffectExpiredEvent {
    public final Entity target;
    public final Object effect; // replaced with HackEffect in Task 2

    public HackEffectExpiredEvent(Entity target, Object effect) {
        this.target = target;
        this.effect = effect;
    }
}
