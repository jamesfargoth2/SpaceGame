package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.hacking.HackEffect;

public final class HackEffectExpiredEvent {
    public final Entity target;
    public final HackEffect effect;

    public HackEffectExpiredEvent(Entity target, HackEffect effect) {
        this.target = target;
        this.effect = effect;
    }
}
