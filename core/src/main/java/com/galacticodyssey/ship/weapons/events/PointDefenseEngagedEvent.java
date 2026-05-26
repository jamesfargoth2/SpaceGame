package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;

public final class PointDefenseEngagedEvent {
    public final Entity shipEntity;
    public final Entity interceptedProjectile;

    public PointDefenseEngagedEvent(Entity shipEntity, Entity interceptedProjectile) {
        this.shipEntity = shipEntity;
        this.interceptedProjectile = interceptedProjectile;
    }
}
