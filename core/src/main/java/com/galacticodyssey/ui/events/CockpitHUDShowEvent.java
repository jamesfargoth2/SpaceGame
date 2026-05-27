package com.galacticodyssey.ui.events;

import com.badlogic.ashley.core.Entity;

public final class CockpitHUDShowEvent {
    public final Entity ship;

    public CockpitHUDShowEvent(Entity ship) {
        this.ship = ship;
    }
}
