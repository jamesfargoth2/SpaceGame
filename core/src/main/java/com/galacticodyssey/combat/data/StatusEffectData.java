package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class StatusEffectData {
    public StatusEffectType type;
    public float duration;
    public float tickRate;
    public float magnitude;
    public int maxStacks = 1;
    public boolean disablesShields;
    public float armorReduction;
    public float moveSpeedMultiplier = 1.0f;
    public boolean preventsActions;
}
