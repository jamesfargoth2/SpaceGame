package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.Map;

public class ConsumableItem extends Item {
    public final float healAmount;
    public final String buffEffect;
    public final float useTime;

    public ConsumableItem(String id, String name, String description, String icon,
                          QualityTier qualityTier, float weight, float healAmount,
                          String buffEffect, float useTime, int maxStack) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, maxStack);
        this.healAmount = healAmount;
        this.buffEffect = buffEffect;
        this.useTime = useTime;
    }

    @Override
    public ItemType getType() {
        return ItemType.CONSUMABLE;
    }

    @Override
    protected void populateCustomData(Map<String, Object> customData) {
        customData.put("healAmount",  healAmount);
        customData.put("buffEffect",  buffEffect);
        customData.put("useTime",     useTime);
    }
}
