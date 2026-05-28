package com.galacticodyssey.ui.events;

import com.badlogic.ashley.core.Entity;

public final class MarketOpenEvent {
    public final Entity station;
    public final Entity player;
    public final Entity ship;

    public MarketOpenEvent(Entity station, Entity player, Entity ship) {
        this.station = station;
        this.player  = player;
        this.ship    = ship;
    }
}
