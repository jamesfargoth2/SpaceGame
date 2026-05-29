package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** A boarding win condition has been met (defenders cleared or bridge captured). */
public final class BoardingClearedEvent {
    public final Entity aggressor;
    public final Entity target;

    public BoardingClearedEvent(Entity aggressor, Entity target) {
        this.aggressor = aggressor;
        this.target = target;
    }
}
