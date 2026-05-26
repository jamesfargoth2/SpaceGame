package com.galacticodyssey.core.events;
import com.badlogic.ashley.core.Entity;

public class PlayerEnterShipEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerEnterShipEvent(Entity player, Entity ship) { this.player = player; this.ship = ship; }
}
