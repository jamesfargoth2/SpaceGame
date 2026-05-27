package com.galacticodyssey.ship.flooding.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a compartment begins taking on water, either from an
 * external hull breach or from cross-flow through a doorway.
 *
 * <p>UI/audio/VFX systems subscribe to this to trigger flooding alarms,
 * water-rush sound effects, and visual indicators on the HUD.
 */
public final class FloodingStartedEvent {

    /** The ship entity that is flooding. */
    public final Entity shipEntity;

    /** ID of the compartment that started flooding. */
    public final String compartmentId;

    /** Whether this compartment is flooding from an external breach (true)
     *  or from cross-flow through a doorway (false). */
    public final boolean fromBreach;

    public FloodingStartedEvent(Entity shipEntity, String compartmentId, boolean fromBreach) {
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
        this.fromBreach = fromBreach;
    }
}
