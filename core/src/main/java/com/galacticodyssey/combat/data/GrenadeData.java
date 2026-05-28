package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class GrenadeData {
    public String id;
    public String displayName;
    public FuseType fuseType = FuseType.TIMED;
    public float fuseDuration = 3.0f;
    public boolean cookable;
    public float throwForce = 18.0f;
    public float mass = 0.4f;
    public float drag = 0.05f;
    public boolean gravity = true;
    public float damage = 50.0f;
    public float blastRadius = 8.0f;
    public float blastFraction = 0.5f;
    public float thermalFraction = 0.1f;
    public float fragmentFraction = 0.4f;
    public boolean isDirectional;
    public float bounceRestitution = 0.3f;
    public int maxBounces = 5;
    public StatusEffectType statusEffect;
    public float statusEffectChance;
    public int maxCarry = 4;
}
