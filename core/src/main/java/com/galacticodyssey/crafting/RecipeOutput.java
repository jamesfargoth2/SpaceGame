package com.galacticodyssey.crafting;

/**
 * Immutable data pair representing an output material and base quantity produced by a recipe.
 */
public final class RecipeOutput {
    public final String materialId;
    public final int baseQuantity;

    public RecipeOutput(String materialId, int baseQuantity) {
        this.materialId = materialId;
        this.baseQuantity = baseQuantity;
    }
}
