package com.galacticodyssey.ship.structure.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

/** Published each tick a breached zone is venting atmosphere, carrying the thrust-like force. */
public final class VentForceEvent {
    public final Entity entity;
    public final Vector3 direction;
    public final float forceMagnitude;

    public VentForceEvent(Entity entity, Vector3 direction, float forceMagnitude) {
        this.entity = entity;
        this.direction = new Vector3(direction);
        this.forceMagnitude = forceMagnitude;
    }
}
