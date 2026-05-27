package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class DebrisImpactEvent {
    public final Entity debris;
    public final Entity target;
    public final Vector3 impactPoint;
    public final float impactEnergy;

    public DebrisImpactEvent(Entity debris, Entity target, Vector3 impactPoint, float impactEnergy) {
        this.debris = debris;
        this.target = target;
        this.impactPoint = impactPoint;
        this.impactEnergy = impactEnergy;
    }
}
