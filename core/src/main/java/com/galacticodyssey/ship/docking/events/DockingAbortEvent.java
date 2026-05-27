package com.galacticodyssey.ship.docking.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a docking approach is aborted because the chaser left the
 * approach corridor or exceeded the maximum approach speed.
 */
public final class DockingAbortEvent {

    /** Reasons a docking approach can be aborted. */
    public enum DockingAbortReason {
        NONE,
        OUT_OF_CORRIDOR,
        TOO_FAST
    }

    /** The chaser entity whose approach was aborted. */
    public final Entity entity;

    /** Why the approach was aborted. */
    public final DockingAbortReason reason;

    public DockingAbortEvent(Entity entity, DockingAbortReason reason) {
        this.entity = entity;
        this.reason = reason;
    }
}
