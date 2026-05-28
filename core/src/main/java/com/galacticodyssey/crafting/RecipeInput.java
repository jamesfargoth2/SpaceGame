package com.galacticodyssey.crafting;

/**
 * Immutable data pair representing a required input material and quantity for a recipe.
 */
public final class RecipeInput {
    public final String materialId;
    public final int quantity;

    public RecipeInput(String materialId, int quantity) {
        this.materialId = materialId;
        this.quantity = quantity;
    }
}
