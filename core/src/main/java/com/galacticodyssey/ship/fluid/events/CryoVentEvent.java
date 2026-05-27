package com.galacticodyssey.ship.fluid.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a cryogenic tank relief valve opens to vent
 * excess vapour pressure. The vented mass is lost to space.
 */
public final class CryoVentEvent {

    public final Entity tankEntity;
    public final float ventedMass;

    public CryoVentEvent(Entity tankEntity, float ventedMass) {
        this.tankEntity = tankEntity;
        this.ventedMass = ventedMass;
    }
}
