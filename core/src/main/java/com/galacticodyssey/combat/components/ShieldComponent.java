package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.ShieldType;

public class ShieldComponent implements Component {
    public float currentShield = 40f;
    public float maxShield = 40f;
    public float rechargeRate = 8f;
    public float rechargeDelay = 4f;
    public float timeSinceLastHit = 0f;
    public ShieldType shieldType = ShieldType.ENERGY;
}
