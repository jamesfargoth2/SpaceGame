package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.Arrays;
import java.util.Map;

public class MeleeWeaponItem extends Item {
    public final WeaponAssembly assembly;

    public MeleeWeaponItem(String id, String name, String description, String icon,
                           QualityTier qualityTier, int gridWidth, int gridHeight,
                           float weight, WeaponAssembly assembly) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.assembly = assembly;
    }

    @Override
    public ItemType getType() {
        return ItemType.MELEE_WEAPON;
    }

    @Override
    protected void populateCustomData(Map<String, Object> customData) {
        customData.put("frameId",        assembly.frameId);
        customData.put("modIds",         Arrays.asList(assembly.modIds));
        customData.put("assemblyQuality", assembly.quality.name());
    }
}
