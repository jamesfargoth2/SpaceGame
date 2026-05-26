package com.galacticodyssey.core.events;
import com.badlogic.ashley.core.Entity;

public class ShipEntryAvailableEvent {
    public final Entity ship;
    public final boolean available;
    public ShipEntryAvailableEvent(Entity ship, boolean available) { this.ship = ship; this.available = available; }
}
