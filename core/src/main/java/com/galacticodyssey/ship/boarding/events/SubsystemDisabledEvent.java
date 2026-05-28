package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;

/** A ship subsystem became non-operational (destroyed or EMP-disabled). */
public final class SubsystemDisabledEvent {
    public final Entity ship;
    public final SubsystemType subsystem;

    public SubsystemDisabledEvent(Entity ship, SubsystemType subsystem) {
        this.ship = ship;
        this.subsystem = subsystem;
    }
}
