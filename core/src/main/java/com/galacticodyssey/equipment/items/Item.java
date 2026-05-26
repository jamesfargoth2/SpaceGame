package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;

public abstract class Item {
    public final String id;
    public final String name;
    public final String description;
    public final String icon;
    public final QualityTier qualityTier;
    public final int gridWidth;
    public final int gridHeight;
    public final float weight;
    public final boolean stackable;
    public final int maxStack;
    public int currentStack;

    protected Item(String id, String name, String description, String icon,
                   QualityTier qualityTier, int gridWidth, int gridHeight,
                   float weight, boolean stackable, int maxStack) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.qualityTier = qualityTier;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.weight = weight;
        this.stackable = stackable;
        this.maxStack = maxStack;
        this.currentStack = 1;
    }

    public abstract ItemType getType();

    public float getTotalWeight() {
        return weight * currentStack;
    }

    public boolean canStackWith(Item other) {
        return stackable && other != null && id.equals(other.id)
            && currentStack + other.currentStack <= maxStack;
    }

    public int getSpaceRemaining() {
        return stackable ? maxStack - currentStack : 0;
    }
}
