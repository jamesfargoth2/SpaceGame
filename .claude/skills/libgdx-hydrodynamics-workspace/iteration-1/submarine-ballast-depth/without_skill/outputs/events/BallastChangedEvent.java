package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Fired when ballast tank levels change significantly.
 * UI systems can subscribe to update ballast indicators.
 */
public final class BallastChangedEvent {

    /** The submarine entity. */
    public final Entity submarine;

    /** Overall fill fraction across all tanks (0-1). */
    public final float overallFillFraction;

    /** Total water mass in ballast tanks (kg). */
    public final float totalWaterMass;

    /** Whether emergency blow is active. */
    public final boolean emergencyBlow;

    public BallastChangedEvent(Entity submarine, float overallFillFraction,
                               float totalWaterMass, boolean emergencyBlow) {
        this.submarine = submarine;
        this.overallFillFraction = overallFillFraction;
        this.totalWaterMass = totalWaterMass;
        this.emergencyBlow = emergencyBlow;
    }
}
