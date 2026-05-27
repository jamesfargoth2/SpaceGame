package com.galacticodyssey.core.blackhole.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when tidal acceleration on an entity exceeds the damage threshold.
 * Subscribers (damage system, VFX, audio) should apply spaghettification
 * effects and structural damage.
 */
public final class TidalDamageEvent {

    /** The entity experiencing dangerous tidal stress. */
    public final Entity entity;

    /** The tidal acceleration magnitude in m/s^2. */
    public final float tidalAcceleration;

    public TidalDamageEvent(Entity entity, float tidalAcceleration) {
        this.entity = entity;
        this.tidalAcceleration = tidalAcceleration;
    }
}
