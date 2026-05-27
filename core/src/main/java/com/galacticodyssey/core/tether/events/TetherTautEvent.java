package com.galacticodyssey.core.tether.events;

import com.badlogic.ashley.core.Entity;

public final class TetherTautEvent {
    public final Entity entity;

    public TetherTautEvent(Entity entity) {
        this.entity = entity;
    }
}
