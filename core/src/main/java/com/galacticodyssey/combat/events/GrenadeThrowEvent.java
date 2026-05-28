package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class GrenadeThrowEvent {
    public final Entity thrower;
    public final Vector3 position;
    public final Vector3 direction;
    public final String grenadeTypeId;

    public GrenadeThrowEvent(Entity thrower, Vector3 position, Vector3 direction, String grenadeTypeId) {
        this.thrower = thrower;
        this.position = new Vector3(position);
        this.direction = new Vector3(direction);
        this.grenadeTypeId = grenadeTypeId;
    }
}
