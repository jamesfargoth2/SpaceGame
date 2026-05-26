package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;

public final class EntityKilledEvent {
    public final Entity target;
    public final Entity killer;

    public EntityKilledEvent(Entity target, Entity killer) {
        this.target = target;
        this.killer = killer;
    }
}
