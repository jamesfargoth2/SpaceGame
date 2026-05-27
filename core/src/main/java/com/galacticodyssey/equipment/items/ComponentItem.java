package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.Map;

public class ComponentItem extends Item {
    public final String componentId;
    public final String componentType;

    public ComponentItem(String id, String name, String description, String icon,
                         QualityTier qualityTier, int gridWidth, int gridHeight,
                         float weight, String componentId, String componentType) {
        super(id, name, description, icon, qualityTier, gridWidth, gridHeight,
              weight, false, 1);
        this.componentId = componentId;
        this.componentType = componentType;
    }

    @Override
    public ItemType getType() {
        return ItemType.COMPONENT;
    }

    @Override
    protected void populateCustomData(Map<String, Object> customData) {
        customData.put("componentId",   componentId);
        customData.put("componentType", componentType);
    }
}
