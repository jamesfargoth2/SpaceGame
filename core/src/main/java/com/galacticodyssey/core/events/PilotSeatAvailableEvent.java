package com.galacticodyssey.core.events;
import com.badlogic.ashley.core.Entity;

public class PilotSeatAvailableEvent {
    public final Entity ship;
    public final boolean available;
    public PilotSeatAvailableEvent(Entity ship, boolean available) { this.ship = ship; this.available = available; }
}
