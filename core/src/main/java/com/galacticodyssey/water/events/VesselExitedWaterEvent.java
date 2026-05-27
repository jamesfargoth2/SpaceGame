package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a vessel's last {@link com.galacticodyssey.water.BuoyancySamplePoint}
 * clears the water surface.
 */
public final class VesselExitedWaterEvent {

    public final Entity vessel;

    public VesselExitedWaterEvent(Entity vessel) {
        this.vessel = vessel;
    }
}
