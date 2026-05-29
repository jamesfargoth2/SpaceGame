package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.BoardingOutcome;

/** A resolution outcome has been selected (by UI, debug key, or AI). */
public final class BoardingResolutionChosenEvent {
    public final Entity target;
    public final BoardingOutcome outcome;

    public BoardingResolutionChosenEvent(Entity target, BoardingOutcome outcome) {
        this.target = target;
        this.outcome = outcome;
    }
}
