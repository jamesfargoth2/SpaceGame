package com.galacticodyssey.ship.fluid.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when an ullage burn is requested to settle propellant
 * toward the engine intake before a main engine ignition.
 */
public final class UllageBurnEvent {

    public final Entity shipEntity;

    public UllageBurnEvent(Entity shipEntity) {
        this.shipEntity = shipEntity;
    }
}
