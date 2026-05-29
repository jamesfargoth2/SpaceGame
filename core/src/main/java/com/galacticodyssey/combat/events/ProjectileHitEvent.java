package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class ProjectileHitEvent {
    public final Entity shooter;
    public final Entity target;
    public final Vector3 hitPoint;
    public final float damage;
    public final DamageType damageType;
    public final float areaOfEffect;
    public final String ammoTypeId;
    public final HitRegion hitRegion;

    public ProjectileHitEvent(Entity shooter, Entity target, Vector3 hitPoint, float damage,
                              DamageType damageType, float areaOfEffect, String ammoTypeId,
                              HitRegion hitRegion) {
        this.shooter = shooter;
        this.target = target;
        this.hitPoint = new Vector3(hitPoint);
        this.damage = damage;
        this.damageType = damageType;
        this.areaOfEffect = areaOfEffect;
        this.ammoTypeId = ammoTypeId;
        this.hitRegion = hitRegion;
    }
}
