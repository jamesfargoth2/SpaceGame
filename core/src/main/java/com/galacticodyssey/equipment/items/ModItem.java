package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class ModItem extends Item {
    public final String weaponModId;

    public ModItem(String id, String name, String description, String icon,
                   QualityTier qualityTier, float weight, String weaponModId) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, false, 1);
        this.weaponModId = weaponModId;
    }

    @Override
    public ItemType getType() {
        return ItemType.MOD;
    }
}
