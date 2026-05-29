package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** The boarding operation is awaiting a resolution choice (UI menu seam). */
public final class BoardingResolutionRequestedEvent {
    public final Entity target;

    public BoardingResolutionRequestedEvent(Entity target) {
        this.target = target;
    }
}
