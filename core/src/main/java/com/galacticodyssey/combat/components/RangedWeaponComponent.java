package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class RangedWeaponComponent implements Component {
    public float damage;
    public float fireRate;
    public float spread;
    public float range;
    public float recoil;
    public Vector2[] recoilPattern;
    public int recoilIndex;
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
    public float fireTimer;
    public int burstShotsRemaining;
    public float burstDelay = 0.05f;
    public float burstTimer;
}
