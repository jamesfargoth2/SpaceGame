package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class RangedWeaponSnapshot {
    public float damage;
    public float fireRate;
    public float spread;
    public float range;
    public float recoil;
    public int currentAmmo;
    public int magSize;
    public float reloadTime;
    public float reloadTimer;
    public boolean reloading;
    public FiringMode firingMode;
    public boolean hitscan;
    public DamageType damageType;
    public StatusEffectType statusEffect;
    public float statusEffectChance;
    public Float projectileSpeed;
    public String ammoTypeId;
    public RangedWeaponSnapshot() {}
}
