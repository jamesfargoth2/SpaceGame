package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;

public final class ReloadStartedEvent {
    public final Entity entity;
    public final float reloadTime;

    public ReloadStartedEvent(Entity entity, float reloadTime) {
        this.entity = entity;
        this.reloadTime = reloadTime;
    }
}
