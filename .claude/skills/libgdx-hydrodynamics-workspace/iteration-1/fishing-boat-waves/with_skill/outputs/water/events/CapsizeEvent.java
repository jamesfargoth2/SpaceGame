package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a vessel's roll exceeds 60 degrees and is not recovering,
 * indicating the vessel has capsized beyond the point of return.
 */
public final class CapsizeEvent {

    public final Entity entity;

    public CapsizeEvent(Entity entity) {
        this.entity = entity;
    }
}
