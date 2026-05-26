package com.galacticodyssey.ship.weapons.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;

public class ShipWeaponData {
    public String id;
    public String name;
    public ShipWeaponCategory category;
    public float damage;
    public DamageType damageType;
    public float fireRate;
    public float projectileSpeed;
    public float range;
    public float energyCost;
    public float heatPerShot;
    public Integer ammoCapacity;
    public Integer currentAmmo;
    public float trackingSpeed;
    public int burstCount = 1;
    public float burstDelay = 0f;

    public boolean hasAmmo() { return ammoCapacity != null; }
    public boolean canFire() { return !hasAmmo() || (currentAmmo != null && currentAmmo > 0); }
    public void consumeAmmo() { if (currentAmmo != null) currentAmmo--; }
}
