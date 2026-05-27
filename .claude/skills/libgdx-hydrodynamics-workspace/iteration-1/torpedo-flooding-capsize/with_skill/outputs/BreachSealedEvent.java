package com.galacticodyssey.ship.flooding.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a hull breach in a compartment is successfully sealed,
 * either by the player completing a repair action or by an automated
 * damage control system.
 *
 * <p>Once sealed, no further external water ingress occurs in the
 * affected compartment. Water already inside remains and must be pumped
 * out separately.
 */
public final class BreachSealedEvent {

    /** The ship entity. */
    public final Entity shipEntity;

    /** ID of the compartment whose breach was sealed. */
    public final String compartmentId;

    public BreachSealedEvent(Entity shipEntity, String compartmentId) {
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
    }
}
