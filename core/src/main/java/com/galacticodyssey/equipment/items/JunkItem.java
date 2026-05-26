package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.Map;
import java.util.HashMap;

public class JunkItem extends Item {
    public final int sellValue;
    public final Map<String, Integer> salvageYields;

    public JunkItem(String id, String name, String description, String icon,
                    QualityTier qualityTier, float weight, int sellValue,
                    Map<String, Integer> salvageYields) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, 10);
        this.sellValue = sellValue;
        this.salvageYields = new HashMap<>(salvageYields);
    }

    @Override
    public ItemType getType() {
        return ItemType.JUNK;
    }
}
