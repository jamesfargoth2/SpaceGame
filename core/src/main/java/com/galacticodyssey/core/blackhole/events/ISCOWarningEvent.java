package com.galacticodyssey.core.blackhole.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when an entity enters the region inside the innermost stable
 * circular orbit (r < 3 * rs). The ship can still escape with thrust, but
 * no stable orbit exists at this distance. UI and audio systems should
 * present a warning to the player.
 */
public final class ISCOWarningEvent {

    /** The entity inside the ISCO boundary. */
    public final Entity entity;

    /** Distance from the entity to the event horizon (schwarzschildRadius). */
    public final float distanceToHorizon;

    public ISCOWarningEvent(Entity entity, float distanceToHorizon) {
        this.entity = entity;
        this.distanceToHorizon = distanceToHorizon;
    }
}
