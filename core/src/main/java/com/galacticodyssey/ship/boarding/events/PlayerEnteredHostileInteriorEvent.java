package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** The player has transitioned into a hostile ship's interior to begin boarding combat. */
public final class PlayerEnteredHostileInteriorEvent {
    public final Entity player;
    public final Entity targetShip;

    public PlayerEnteredHostileInteriorEvent(Entity player, Entity targetShip) {
        this.player = player;
        this.targetShip = targetShip;
    }
}
