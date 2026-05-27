package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.MeleeWeaponSnapshot;
import java.util.EnumMap;
import java.util.Map;

public class MeleeWeaponComponent implements Component, Snapshotable<MeleeWeaponSnapshot> {
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

    @Override
    public MeleeWeaponSnapshot takeSnapshot() {
        MeleeWeaponSnapshot s = new MeleeWeaponSnapshot();
        s.baseDamage = baseDamage;
        s.reach = reach;
        s.swingSpeed = swingSpeed;
        s.blockEfficiency = blockEfficiency;
        s.damageType = damageType;
        s.weightClass = weightClass;
        s.directionalModifiers.putAll(directionalModifiers);
        s.staminaCosts.putAll(staminaCosts);
        return s;
    }

    @Override
    public void restoreFromSnapshot(MeleeWeaponSnapshot s) {
        baseDamage = s.baseDamage;
        reach = s.reach;
        swingSpeed = s.swingSpeed;
        blockEfficiency = s.blockEfficiency;
        damageType = s.damageType;
        weightClass = s.weightClass;
        directionalModifiers.clear();
        directionalModifiers.putAll(s.directionalModifiers);
        staminaCosts.clear();
        staminaCosts.putAll(s.staminaCosts);
    }
}
