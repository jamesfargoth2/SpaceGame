package com.galacticodyssey.core.solar.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when an entity receives a radiation dose from solar wind,
 * CME exposure, or radiation belt traversal.
 */
public final class RadiationDoseEvent {
    public final Entity entity;
    public final float dose;

    public RadiationDoseEvent(Entity entity, float dose) {
        this.entity = entity;
        this.dose = dose;
    }
}
