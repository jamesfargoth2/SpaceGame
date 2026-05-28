package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;

/** A ship has taken weapon damage at a world-space hit position. */
public final class ShipDamageEvent {
    public final Entity target;
    public final Entity attacker;
    public final float damage;
    public final DamageType damageType;
    public final Vector3 hitPosition;

    public ShipDamageEvent(Entity target, Entity attacker, float damage,
                           DamageType damageType, Vector3 hitPosition) {
        this.target = target;
        this.attacker = attacker;
        this.damage = damage;
        this.damageType = damageType;
        this.hitPosition = new Vector3(hitPosition);
    }
}
