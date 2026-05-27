package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;

public final class TargetLockedEvent {
    public final Entity target;

    public TargetLockedEvent(Entity target) {
        this.target = target;
    }
}
