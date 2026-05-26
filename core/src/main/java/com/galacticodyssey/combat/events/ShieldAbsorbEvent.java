package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;

public final class ShieldAbsorbEvent {
    public final Entity target;
    public final float absorbed;
    public final float shieldRemaining;

    public ShieldAbsorbEvent(Entity target, float absorbed, float shieldRemaining) {
        this.target = target;
        this.absorbed = absorbed;
        this.shieldRemaining = shieldRemaining;
    }
}
