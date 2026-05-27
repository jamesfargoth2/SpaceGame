package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when the free-surface effect from partially flooded
 * compartments reduces the vessel's effective metacentric height
 * past a dangerous threshold. UI/audio can warn the player about
 * imminent capsizing.
 */
public final class StabilityWarningEvent {

    public final Entity entity;

    /** Loss of righting lever GZ in metres due to free-surface sloshing. */
    public final float gzLoss;

    public StabilityWarningEvent(Entity entity, float gzLoss) {
        this.entity = entity;
        this.gzLoss = gzLoss;
    }
}
