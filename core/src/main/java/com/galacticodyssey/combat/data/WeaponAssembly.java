package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.QualityTier;

public class WeaponAssembly {
    public final String frameId;
    public final String barrelId;
    public final String ammoTypeId;
    public final String[] modIds;
    public final QualityTier quality;
    public final boolean isMelee;

    public WeaponAssembly(String frameId, String barrelId, String ammoTypeId,
                          String[] modIds, QualityTier quality, boolean isMelee) {
        this.frameId = frameId;
        this.barrelId = barrelId;
        this.ammoTypeId = ammoTypeId;
        this.modIds = modIds != null ? modIds : new String[0];
        this.quality = quality;
        this.isMelee = isMelee;
    }

    public static WeaponAssembly melee(String frameId, QualityTier quality) {
        return new WeaponAssembly(frameId, null, null, null, quality, true);
    }

    public static WeaponAssembly ranged(String frameId, String barrelId, String ammoTypeId,
                                         String[] modIds, QualityTier quality) {
        return new WeaponAssembly(frameId, barrelId, ammoTypeId, modIds, quality, false);
    }
}
