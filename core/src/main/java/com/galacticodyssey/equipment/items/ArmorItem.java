package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.EnumMap;
import java.util.Map;

public class ArmorItem extends Item {
    public final float armorRating;
    public final Map<DamageType, Float> resistances;
    public final EquipmentSlot slotType;
    public float durability;
    public final float maxDurability;

    public ArmorItem(String id, String name, String description, String icon,
                     QualityTier qualityTier, int gridWidth, int gridHeight,
                     float weight, float armorRating, Map<DamageType, Float> resistances,
                     EquipmentSlot slotType, float maxDurability) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.armorRating = armorRating;
        this.resistances = new EnumMap<>(resistances);
        this.slotType = slotType;
        this.maxDurability = maxDurability;
        this.durability = maxDurability;
    }

    @Override
    public ItemType getType() {
        return ItemType.ARMOR;
    }
}
