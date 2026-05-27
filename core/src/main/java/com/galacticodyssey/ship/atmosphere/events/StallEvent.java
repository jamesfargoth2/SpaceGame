package com.galacticodyssey.ship.atmosphere.events;

import com.badlogic.ashley.core.Entity;

/** Published when an aerodynamic entity exceeds its stall angle. */
public final class StallEvent {

    public final Entity entity;
    public final float angleOfAttack;

    public StallEvent(Entity entity, float angleOfAttack) {
        this.entity = entity;
        this.angleOfAttack = angleOfAttack;
    }
}
