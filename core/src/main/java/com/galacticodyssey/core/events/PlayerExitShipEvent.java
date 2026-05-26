package com.galacticodyssey.core.events;
import com.badlogic.ashley.core.Entity;

public class PlayerExitShipEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerExitShipEvent(Entity player, Entity ship) { this.player = player; this.ship = ship; }
}
