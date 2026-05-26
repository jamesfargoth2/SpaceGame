package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.MeleeCategory;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import java.util.HashMap;
import java.util.Map;

public class MeleeFrameData {
    public String id;
    public MeleeCategory category;
    public float baseDamage;
    public float baseSpeed;
    public float baseReach;
    public float baseBlockEfficiency;
    public float weight;
    public DamageType damageType;
    public WeightClass weightClass = WeightClass.MEDIUM;
    public Map<String, Float> directionalModifiers = new HashMap<>();
}
