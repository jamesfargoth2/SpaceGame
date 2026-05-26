package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

public final class RecoilEvent {
    public final Entity entity;
    public final Vector2 recoilOffset;

    public RecoilEvent(Entity entity, Vector2 recoilOffset) {
        this.entity = entity;
        this.recoilOffset = new Vector2(recoilOffset);
    }
}
