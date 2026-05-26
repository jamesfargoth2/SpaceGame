package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class AmmoTypeData {
    public String id;
    public DamageType damageType;
    public float damageMultiplier = 1.0f;
    public StatusEffectType statusEffect;
    public float statusEffectChance;
    public Float projectileSpeed;
}
