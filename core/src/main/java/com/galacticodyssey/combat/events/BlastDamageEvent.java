package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class BlastDamageEvent {
    public final Entity target;
    public final float damage;
    public final Vector3 blastOrigin;
    public final Vector3 impulse;

    public BlastDamageEvent(Entity target, float damage, Vector3 blastOrigin, Vector3 impulse) {
        this.target = target;
        this.damage = damage;
        this.blastOrigin = new Vector3(blastOrigin);
        this.impulse = new Vector3(impulse);
    }
}
