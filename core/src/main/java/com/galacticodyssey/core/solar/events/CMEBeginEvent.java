package com.galacticodyssey.core.solar.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a coronal mass ejection begins on a solar source.
 */
public final class CMEBeginEvent {
    public final Entity star;
    public final float intensity;
    public final float duration;

    public CMEBeginEvent(Entity star, float intensity, float duration) {
        this.star = star;
        this.intensity = intensity;
        this.duration = duration;
    }
}
