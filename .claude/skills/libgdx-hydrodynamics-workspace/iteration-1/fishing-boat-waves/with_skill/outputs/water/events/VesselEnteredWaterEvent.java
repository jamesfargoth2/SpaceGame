package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a vessel's first {@link com.galacticodyssey.water.BuoyancySamplePoint}
 * submerges below the water surface. Other systems (audio, VFX, drag switching)
 * subscribe to react to splashdown or water entry.
 */
public final class VesselEnteredWaterEvent {

    public final Entity vessel;
    public final Entity waterBody;

    public VesselEnteredWaterEvent(Entity vessel, Entity waterBody) {
        this.vessel = vessel;
        this.waterBody = waterBody;
    }
}
