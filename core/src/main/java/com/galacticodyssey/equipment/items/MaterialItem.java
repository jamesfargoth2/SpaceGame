package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import java.util.Map;

public class MaterialItem extends Item {
    private final String materialId;
    private final MaterialTier tier;
    private final MaterialCategory category;
    private final String commodityLink;

    public MaterialItem(String id, String name, String description, String icon,
                        QualityTier qualityTier, float weight, int maxStack,
                        String materialId, MaterialTier tier,
                        MaterialCategory category, String commodityLink) {
        super(id, name, description, icon, qualityTier, 1, 1, weight, true, maxStack);
        this.materialId = materialId;
        this.tier = tier;
        this.category = category;
        this.commodityLink = commodityLink;
    }

    @Override
    public ItemType getType() {
        return ItemType.MATERIAL;
    }

    @Override
    protected void populateCustomData(Map<String, Object> customData) {
        customData.put("materialId", materialId);
        customData.put("tier", tier.name());
        customData.put("category", category.name());
        if (commodityLink != null) {
            customData.put("commodityLink", commodityLink);
        }
    }

    public String getMaterialId() {
        return materialId;
    }

    public MaterialTier getTier() {
        return tier;
    }

    public MaterialCategory getCategory() {
        return category;
    }

    public String getCommodityLink() {
        return commodityLink;
    }
}
