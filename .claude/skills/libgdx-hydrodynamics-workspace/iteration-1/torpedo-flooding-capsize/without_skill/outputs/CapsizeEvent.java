package com.galacticodyssey.ship.flooding.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when the ship's roll exceeds 60 degrees and has not recovered
 * for a sustained duration. At this point the ship is considered capsized
 * and unlikely to right itself without external intervention.
 *
 * <p>Game-over logic, audio alarms, and camera effects subscribe to this.
 */
public final class CapsizeEvent {

    /** The ship entity that has capsized. */
    public final Entity shipEntity;

    /** Final roll angle at the moment of capsize declaration. */
    public final float rollDeg;

    /** Total flooded mass at capsize in kg. */
    public final float totalFloodedMass;

    public CapsizeEvent(Entity shipEntity, float rollDeg, float totalFloodedMass) {
        this.shipEntity = shipEntity;
        this.rollDeg = rollDeg;
        this.totalFloodedMass = totalFloodedMass;
    }
}
