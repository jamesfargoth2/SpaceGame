package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class ProjectileHitEvent {
    public final Entity shooter;
    /** Null when the bullet hits world geometry with no associated entity. */
    public final Entity target;
    public final Vector3 hitPoint;
    /** Surface normal at the impact point; null for legacy entity-only hits. */
    public final Vector3 hitNormal;
    public final float damage;
    public final DamageType damageType;
    public final float areaOfEffect;
    public final String ammoTypeId;
    public final HitRegion hitRegion;

    /** Full constructor used by the Bullet-physics raycast path. */
    public ProjectileHitEvent(Entity shooter, Entity target, Vector3 hitPoint, Vector3 hitNormal,
                              float damage, DamageType damageType, float areaOfEffect,
                              String ammoTypeId, HitRegion hitRegion) {
        this.shooter = shooter;
        this.target = target;
        this.hitPoint = new Vector3(hitPoint);
        this.hitNormal = hitNormal != null ? new Vector3(hitNormal) : null;
        this.damage = damage;
        this.damageType = damageType;
        this.areaOfEffect = areaOfEffect;
        this.ammoTypeId = ammoTypeId;
        this.hitRegion = hitRegion;
    }

    /** Legacy constructor (entity capsule CCD path) — hitNormal is null. */
    public ProjectileHitEvent(Entity shooter, Entity target, Vector3 hitPoint, float damage,
                              DamageType damageType, float areaOfEffect, String ammoTypeId,
                              HitRegion hitRegion) {
        this(shooter, target, hitPoint, null, damage, damageType, areaOfEffect, ammoTypeId, hitRegion);
    }
}
