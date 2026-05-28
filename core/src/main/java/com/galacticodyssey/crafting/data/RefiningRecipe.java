package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.RecipeCategory;
import com.galacticodyssey.crafting.RecipeInput;
import com.galacticodyssey.crafting.RecipeOutput;

import java.util.List;

/**
 * Immutable data definition for a single refining recipe.
 * Describes how input materials are transformed into output materials,
 * along with tier requirements, processing time, and power cost.
 */
public class RefiningRecipe {
    public final String recipeId;
    public final String name;
    public final RecipeCategory category;
    public final int requiredTier;
    public final List<RecipeInput> inputs;
    public final List<RecipeOutput> outputs;
    public final float processingTime;
    public final float powerCost;

    public RefiningRecipe(String recipeId, String name, RecipeCategory category,
                          int requiredTier, List<RecipeInput> inputs,
                          List<RecipeOutput> outputs, float processingTime,
                          float powerCost) {
        this.recipeId = recipeId;
        this.name = name;
        this.category = category;
        this.requiredTier = requiredTier;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.processingTime = processingTime;
        this.powerCost = powerCost;
    }
}
