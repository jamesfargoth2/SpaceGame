package com.galacticodyssey.mech.events;

import com.badlogic.gdx.math.Vector3;

/**
 * Published when a mech lands with enough speed to potentially cause damage.
 * Subscribers can trigger camera shake, VFX, and damage application.
 */
public final class MechImpactEvent {

    public final Vector3 position;
    public final float impactVelocity;
    public final float damage;

    public MechImpactEvent(Vector3 position, float impactVelocity, float damage) {
        this.position = new Vector3(position);
        this.impactVelocity = impactVelocity;
        this.damage = damage;
    }
}
