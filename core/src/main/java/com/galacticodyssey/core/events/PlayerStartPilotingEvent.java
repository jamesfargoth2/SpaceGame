package com.galacticodyssey.core.events;
import com.badlogic.ashley.core.Entity;

public class PlayerStartPilotingEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerStartPilotingEvent(Entity player, Entity ship) { this.player = player; this.ship = ship; }
}
