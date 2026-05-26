package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public class WeaponItem extends Item {
    public final WeaponAssembly assembly;

    public WeaponItem(String id, String name, String description, String icon,
                      QualityTier qualityTier, int gridWidth, int gridHeight,
                      float weight, WeaponAssembly assembly) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.assembly = assembly;
    }

    @Override
    public ItemType getType() {
        return ItemType.WEAPON;
    }
}
