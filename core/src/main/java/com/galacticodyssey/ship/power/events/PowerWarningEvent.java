package com.galacticodyssey.ship.power.events;

import com.badlogic.ashley.core.Entity;

public class PowerWarningEvent {
    public final Entity ship;
    public final String subsystem;
    public final float allocationRatio;

    public PowerWarningEvent(Entity ship, String subsystem, float allocationRatio) {
        this.ship = ship;
        this.subsystem = subsystem;
        this.allocationRatio = allocationRatio;
    }
}
