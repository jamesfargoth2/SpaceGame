package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public final class DetonationEvent {
    public final Entity owner;
    public final Vector3 position;
    public final float damage;
    public final DamageType damageType;
    public final float areaOfEffect;

    public DetonationEvent(Entity owner, Vector3 position, float damage,
                           DamageType damageType, float areaOfEffect) {
        this.owner = owner;
        this.position = new Vector3(position);
        this.damage = damage;
        this.damageType = damageType;
        this.areaOfEffect = areaOfEffect;
    }
}
