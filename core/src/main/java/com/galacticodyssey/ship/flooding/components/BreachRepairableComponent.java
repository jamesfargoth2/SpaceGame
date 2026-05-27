package com.galacticodyssey.ship.flooding.components;

import com.badlogic.ashley.core.Component;

/**
 * Marker component attached to the ship entity indicating that the
 * player (or an automated damage control system) can attempt to seal
 * hull breaches in this ship's compartments.
 *
 * <p>The interaction system checks for this component when the player
 * presses the repair key near a breached compartment.
 */
public class BreachRepairableComponent implements Component {

    /**
     * Time in seconds required to seal a single breach. During this
     * time the player must remain near the breach location.
     */
    public float repairTimeSeconds = 5.0f;

    /**
     * Current repair progress in seconds. Resets if the player moves
     * away or interrupts the repair.
     */
    public float currentRepairProgress;

    /**
     * ID of the compartment currently being repaired, or null if no
     * repair is in progress.
     */
    public String repairingCompartmentId;

    /** Whether a repair action is currently in progress. */
    public boolean repairing;
}
