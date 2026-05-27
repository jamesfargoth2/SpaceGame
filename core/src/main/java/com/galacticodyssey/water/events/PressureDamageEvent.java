package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PressureDamageEvent {
    public final Entity entity;
    public final float damage;
    public final float ambientPressure;
    public final float gearMaxPressure;

    public PressureDamageEvent(Entity entity, float damage,
                                float ambientPressure, float gearMaxPressure) {
        this.entity = entity;
        this.damage = damage;
        this.ambientPressure = ambientPressure;
        this.gearMaxPressure = gearMaxPressure;
    }
}
