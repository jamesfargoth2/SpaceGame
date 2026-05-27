package com.galacticodyssey.core.tether.events;

import com.badlogic.ashley.core.Entity;

public final class TetherBreakEvent {
    public final Entity entity;
    public final float breakingTension;

    public TetherBreakEvent(Entity entity, float breakingTension) {
        this.entity = entity;
        this.breakingTension = breakingTension;
    }
}
