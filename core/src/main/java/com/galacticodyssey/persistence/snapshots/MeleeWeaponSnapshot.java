package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import java.util.EnumMap;
import java.util.Map;

public class MeleeWeaponSnapshot {
    public float baseDamage;
    public float reach;
    public float swingSpeed;
    public float blockEfficiency;
    public DamageType damageType;
    public WeightClass weightClass;
    public Map<AttackDirection, Float> directionalModifiers = new EnumMap<>(AttackDirection.class);
    public Map<AttackDirection, Float> staminaCosts = new EnumMap<>(AttackDirection.class);
    public MeleeWeaponSnapshot() {}
}
