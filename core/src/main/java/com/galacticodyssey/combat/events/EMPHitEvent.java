package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;

public final class EMPHitEvent {
    public final Entity target;
    public final float effectStrength;

    public EMPHitEvent(Entity target, float effectStrength) {
        this.target = target;
        this.effectStrength = effectStrength;
    }
}
