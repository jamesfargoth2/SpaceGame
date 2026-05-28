package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;

/**
 * Immutable data definition for a single material type.
 * Loaded from JSON data files at runtime; programmatically constructed in tests.
 */
public class MaterialDefinition {
    public final String materialId;
    public final String name;
    public final MaterialTier tier;
    public final MaterialCategory category;
    public final float weight;
    public final float volume;
    public final int maxStack;
    public final String description;
    /** Links this material to a commodity in the economy system. Null if not tradeable. */
    public final String commodityLink;

    public MaterialDefinition(String materialId, String name, MaterialTier tier,
                              MaterialCategory category, float weight, float volume,
                              int maxStack, String description, String commodityLink) {
        this.materialId = materialId;
        this.name = name;
        this.tier = tier;
        this.category = category;
        this.weight = weight;
        this.volume = volume;
        this.maxStack = maxStack;
        this.description = description;
        this.commodityLink = commodityLink;
    }

    /** No-arg constructor for JSON deserialization. */
    public MaterialDefinition() {
        this.materialId = "";
        this.name = "";
        this.tier = MaterialTier.RAW;
        this.category = MaterialCategory.METAL;
        this.weight = 0f;
        this.volume = 0f;
        this.maxStack = 0;
        this.description = "";
        this.commodityLink = null;
    }
}
