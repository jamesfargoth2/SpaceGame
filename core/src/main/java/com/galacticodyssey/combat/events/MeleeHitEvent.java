package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class MeleeHitEvent {
    public final Entity attacker;
    public final Entity target;
    public final AttackDirection direction;
    public final HitRegion hitRegion;
    public final float damage;
    public final DamageType damageType;

    public MeleeHitEvent(Entity attacker, Entity target, AttackDirection direction,
                         HitRegion hitRegion, float damage, DamageType damageType) {
        this.attacker = attacker;
        this.target = target;
        this.direction = direction;
        this.hitRegion = hitRegion;
        this.damage = damage;
        this.damageType = damageType;
    }
}
