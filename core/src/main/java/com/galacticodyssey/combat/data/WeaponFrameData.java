package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.WeaponCategory;

public class WeaponFrameData {
    public String id;
    public String name;
    public WeaponCategory category;
    public float baseDamage;
    public float baseFireRate;
    public float baseSpread;
    public float baseRecoil;
    public int magSize;
    public int modSlotCount;
    public float weight;
    public FiringMode firingMode;
    public boolean hitscan;
    public float range = 100f;
    public float reloadTime = 2.0f;
}
