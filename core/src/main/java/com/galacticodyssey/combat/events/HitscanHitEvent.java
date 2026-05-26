package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class HitscanHitEvent {
    public final Entity shooter;
    public final Entity target;
    public final Vector3 hitPoint;
    public final Vector3 hitNormal;
    public final HitRegion hitRegion;
    public final float damage;
    public final DamageType damageType;
    public final String ammoTypeId;

    public HitscanHitEvent(Entity shooter, Entity target, Vector3 hitPoint, Vector3 hitNormal,
                           HitRegion hitRegion, float damage, DamageType damageType, String ammoTypeId) {
        this.shooter = shooter;
        this.target = target;
        this.hitPoint = new Vector3(hitPoint);
        this.hitNormal = new Vector3(hitNormal);
        this.hitRegion = hitRegion;
        this.damage = damage;
        this.damageType = damageType;
        this.ammoTypeId = ammoTypeId;
    }
}
