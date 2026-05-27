package com.galacticodyssey.ship.thermal.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a ship component takes thermal damage from exceeding its
 * maximum safe temperature.
 */
public final class ThermalDamageEvent {
    public final Entity entity;
    public final String componentName;
    public final float damageAmount;

    public ThermalDamageEvent(Entity entity, String componentName, float damageAmount) {
        this.entity = entity;
        this.componentName = componentName;
        this.damageAmount = damageAmount;
    }
}
