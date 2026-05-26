package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;

public final class MissileLockedEvent {
    public final Entity targetEntity;
    public final Entity missileEntity;

    public MissileLockedEvent(Entity targetEntity, Entity missileEntity) {
        this.targetEntity = targetEntity;
        this.missileEntity = missileEntity;
    }
}
