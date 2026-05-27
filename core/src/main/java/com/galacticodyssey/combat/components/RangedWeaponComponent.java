package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.RangedWeaponSnapshot;

public class RangedWeaponComponent implements Component, Snapshotable<RangedWeaponSnapshot> {
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

    @Override
    public RangedWeaponSnapshot takeSnapshot() {
        RangedWeaponSnapshot s = new RangedWeaponSnapshot();
        s.damage = damage;
        s.fireRate = fireRate;
        s.spread = spread;
        s.range = range;
        s.recoil = recoil;
        s.currentAmmo = currentAmmo;
        s.magSize = magSize;
        s.reloadTime = reloadTime;
        s.reloadTimer = reloadTimer;
        s.reloading = reloading;
        s.firingMode = firingMode;
        s.hitscan = hitscan;
        s.damageType = damageType;
        s.statusEffect = statusEffect;
        s.statusEffectChance = statusEffectChance;
        s.projectileSpeed = projectileSpeed;
        s.ammoTypeId = ammoTypeId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(RangedWeaponSnapshot s) {
        damage = s.damage;
        fireRate = s.fireRate;
        spread = s.spread;
        range = s.range;
        recoil = s.recoil;
        currentAmmo = s.currentAmmo;
        magSize = s.magSize;
        reloadTime = s.reloadTime;
        reloadTimer = s.reloadTimer;
        reloading = s.reloading;
        firingMode = s.firingMode;
        hitscan = s.hitscan;
        damageType = s.damageType;
        statusEffect = s.statusEffect;
        statusEffectChance = s.statusEffectChance;
        projectileSpeed = s.projectileSpeed;
        ammoTypeId = s.ammoTypeId;
    }
}
