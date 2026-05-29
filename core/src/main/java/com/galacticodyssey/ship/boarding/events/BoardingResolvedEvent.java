package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.BoardingOutcome;

/** A boarding operation has been resolved and applied. */
public final class BoardingResolvedEvent {
    public final Entity target;
    public final BoardingOutcome outcome;

    public BoardingResolvedEvent(Entity target, BoardingOutcome outcome) {
        this.target = target;
        this.outcome = outcome;
    }
}
