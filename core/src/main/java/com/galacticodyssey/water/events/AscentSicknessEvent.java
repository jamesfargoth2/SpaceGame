package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class AscentSicknessEvent {
    public final Entity player;
    public final float ascentSpeed;
    public final float duration;

    public AscentSicknessEvent(Entity player, float ascentSpeed, float duration) {
        this.player = player;
        this.ascentSpeed = ascentSpeed;
        this.duration = duration;
    }
}
