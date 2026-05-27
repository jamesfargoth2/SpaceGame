package com.galacticodyssey.ship.docking.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when two docking ports successfully soft-capture and latch together.
 */
public final class DockingCaptureEvent {

    /** Entity owning the first port. */
    public final Entity portA;

    /** Entity owning the second port. */
    public final Entity portB;

    public DockingCaptureEvent(Entity portA, Entity portB) {
        this.portA = portA;
        this.portB = portB;
    }
}
