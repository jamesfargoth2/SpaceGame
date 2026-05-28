package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class GrenadeBounceEvent {
    public final Entity grenade;
    public final Vector3 position;
    public final Vector3 surfaceNormal;

    public GrenadeBounceEvent(Entity grenade, Vector3 position, Vector3 surfaceNormal) {
        this.grenade = grenade;
        this.position = new Vector3(position);
        this.surfaceNormal = new Vector3(surfaceNormal);
    }
}
