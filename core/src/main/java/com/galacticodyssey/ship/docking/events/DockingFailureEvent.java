package com.galacticodyssey.ship.docking.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a docking capture attempt fails due to excessive speed,
 * misalignment, or roll mismatch.
 */
public final class DockingFailureEvent {

    /** Reasons a docking capture can fail at contact. */
    public enum DockingFailureReason {
        IMPACT_TOO_FAST,
        MISALIGNED,
        ROLL_MISMATCH
    }

    /** Entity owning the first port. */
    public final Entity portA;

    /** Entity owning the second port. */
    public final Entity portB;

    /** Why the capture failed. */
    public final DockingFailureReason reason;

    /** Relative closing speed at the moment of failure (m/s). */
    public final float closingSpeed;

    public DockingFailureEvent(Entity portA, Entity portB,
                               DockingFailureReason reason, float closingSpeed) {
        this.portA = portA;
        this.portB = portB;
        this.reason = reason;
        this.closingSpeed = closingSpeed;
    }
}
