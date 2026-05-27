package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.ShieldType;

public class ShieldSnapshot {
    public float currentShield;
    public float maxShield;
    public float rechargeRate;
    public float rechargeDelay;
    public float timeSinceLastHit;
    public ShieldType shieldType;
    public ShieldSnapshot() {}
}
