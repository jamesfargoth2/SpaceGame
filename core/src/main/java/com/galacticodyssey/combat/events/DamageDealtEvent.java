package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class DamageDealtEvent {
    public final Entity target;
    public final Entity attacker;
    public final float finalDamage;
    public final DamageType damageType;
    public final HitRegion hitRegion;

    public DamageDealtEvent(Entity target, Entity attacker, float finalDamage,
                            DamageType damageType, HitRegion hitRegion) {
        this.target = target;
        this.attacker = attacker;
        this.finalDamage = finalDamage;
        this.damageType = damageType;
        this.hitRegion = hitRegion;
    }
}
