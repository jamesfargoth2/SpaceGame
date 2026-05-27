package com.galacticodyssey.core.solar.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a coronal mass ejection ends on a solar source.
 */
public final class CMEEndEvent {
    public final Entity star;

    public CMEEndEvent(Entity star) {
        this.star = star;
    }
}
