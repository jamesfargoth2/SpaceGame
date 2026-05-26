package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class AmmoItem extends Item {
    public final String ammoTypeId;

    public AmmoItem(String id, String name, String description, String icon,
                    QualityTier qualityTier, float weight, String ammoTypeId, int maxStack) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, maxStack);
        this.ammoTypeId = ammoTypeId;
    }

    @Override
    public ItemType getType() {
        return ItemType.AMMO;
    }
}
