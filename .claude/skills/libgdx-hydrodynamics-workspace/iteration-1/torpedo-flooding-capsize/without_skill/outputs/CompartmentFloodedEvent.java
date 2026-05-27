package com.galacticodyssey.ship.flooding.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a compartment reaches 100% flood level.
 * Systems can subscribe to trigger consequences like equipment failure,
 * crew evacuation, or loss of compartment functionality.
 */
public final class CompartmentFloodedEvent {

    /** The ship entity containing the flooded compartment. */
    public final Entity shipEntity;

    /** ID of the compartment that is now fully flooded. */
    public final String compartmentId;

    /** Total water mass in this compartment (kg). */
    public final float waterMassKg;

    public CompartmentFloodedEvent(Entity shipEntity, String compartmentId, float waterMassKg) {
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
        this.waterMassKg = waterMassKg;
    }
}
