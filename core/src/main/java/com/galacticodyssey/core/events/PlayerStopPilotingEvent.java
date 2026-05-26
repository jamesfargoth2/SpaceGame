package com.galacticodyssey.core.events;
import com.badlogic.ashley.core.Entity;

public class PlayerStopPilotingEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerStopPilotingEvent(Entity player, Entity ship) { this.player = player; this.ship = ship; }
}
