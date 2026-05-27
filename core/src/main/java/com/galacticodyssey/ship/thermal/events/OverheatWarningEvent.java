package com.galacticodyssey.ship.thermal.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a ship component exceeds its maximum safe temperature,
 * before actual thermal damage occurs.
 */
public final class OverheatWarningEvent {
    public final Entity entity;
    public final String componentName;
    public final float currentTemp;
    public final float maxSafeTemp;

    public OverheatWarningEvent(Entity entity, String componentName,
                                float currentTemp, float maxSafeTemp) {
        this.entity = entity;
        this.componentName = componentName;
        this.currentTemp = currentTemp;
        this.maxSafeTemp = maxSafeTemp;
    }
}
