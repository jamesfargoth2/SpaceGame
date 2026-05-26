package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import java.util.EnumMap;
import java.util.Map;

public class MeleeWeaponComponent implements Component {
    public float baseDamage;
    public float reach;
    public float swingSpeed;
    public float blockEfficiency;
    public DamageType damageType;
    public WeightClass weightClass;
    public final Map<AttackDirection, Float> directionalModifiers = new EnumMap<>(AttackDirection.class);
    public final Map<AttackDirection, Float> staminaCosts = new EnumMap<>(AttackDirection.class);

    public MeleeWeaponComponent() {
        for (AttackDirection dir : AttackDirection.values()) {
            directionalModifiers.put(dir, 1.0f);
            staminaCosts.put(dir, 15f);
        }
        staminaCosts.put(AttackDirection.OVERHEAD, 20f);
    }
}
