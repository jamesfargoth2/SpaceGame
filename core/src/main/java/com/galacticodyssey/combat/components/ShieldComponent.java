package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.ShieldType;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShieldSnapshot;

public class ShieldComponent implements Component, Snapshotable<ShieldSnapshot> {
    public float currentShield = 40f;
    public float maxShield = 40f;
    public float rechargeRate = 8f;
    public float rechargeDelay = 4f;
    public float timeSinceLastHit = 0f;
    public ShieldType shieldType = ShieldType.ENERGY;

    @Override
    public ShieldSnapshot takeSnapshot() {
        ShieldSnapshot s = new ShieldSnapshot();
        s.currentShield = currentShield;
        s.maxShield = maxShield;
        s.rechargeRate = rechargeRate;
        s.rechargeDelay = rechargeDelay;
        s.timeSinceLastHit = timeSinceLastHit;
        s.shieldType = shieldType;
        return s;
    }

    @Override
    public void restoreFromSnapshot(ShieldSnapshot s) {
        currentShield = s.currentShield;
        maxShield = s.maxShield;
        rechargeRate = s.rechargeRate;
        rechargeDelay = s.rechargeDelay;
        timeSinceLastHit = s.timeSinceLastHit;
        shieldType = s.shieldType;
    }
}
