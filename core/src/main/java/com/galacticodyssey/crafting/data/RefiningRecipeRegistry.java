package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.RecipeCategory;
import com.galacticodyssey.crafting.RecipeInput;
import com.galacticodyssey.crafting.RecipeOutput;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores {@link RefiningRecipe}s by ID. Supports lookup by ID, tier, and output material.
 * Populated programmatically in tests; loaded from JSON data files at runtime.
 */
public class RefiningRecipeRegistry {
    private final Map<String, RefiningRecipe> byId = new HashMap<>();

    /**
     * Registers a refining recipe. Throws if a recipe with the same ID already exists.
     */
    public void register(RefiningRecipe recipe) {
        if (byId.containsKey(recipe.recipeId)) {
            throw new IllegalArgumentException(
                "Duplicate recipe ID: " + recipe.recipeId);
        }
        byId.put(recipe.recipeId, recipe);
    }

    /** Returns the recipe for the given ID, or null if not found. */
    public RefiningRecipe getRecipe(String recipeId) {
        return byId.get(recipeId);
    }

    /** Returns all recipes where requiredTier <= the given tier. */
    public List<RefiningRecipe> getRecipesForTier(int tier) {
        List<RefiningRecipe> result = new ArrayList<>();
        for (RefiningRecipe recipe : byId.values()) {
            if (recipe.requiredTier <= tier) {
                result.add(recipe);
            }
        }
        return result;
    }

    /** Returns all recipes that produce the given material ID as an output. */
    public List<RefiningRecipe> getRecipesProducing(String materialId) {
        List<RefiningRecipe> result = new ArrayList<>();
        for (RefiningRecipe recipe : byId.values()) {
            for (RecipeOutput output : recipe.outputs) {
                if (output.materialId.equals(materialId)) {
                    result.add(recipe);
                    break;
                }
            }
        }
        return result;
    }

    /** Returns all registered recipes. */
    public List<RefiningRecipe> getAll() {
        return new ArrayList<>(byId.values());
    }

    /**
     * Validates that all input and output material IDs referenced by recipes
     * exist in the given {@link MaterialRegistry}.
     *
     * @return true if all material references are valid, false otherwise
     */
    public boolean validate(MaterialRegistry materialRegistry) {
        for (RefiningRecipe recipe : byId.values()) {
            for (RecipeInput input : recipe.inputs) {
                if (!materialRegistry.contains(input.materialId)) {
                    return false;
                }
            }
            for (RecipeOutput output : recipe.outputs) {
                if (!materialRegistry.contains(output.materialId)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Loads refining recipes from {@code data/crafting/refining_recipes.json}.
     * Requires a libGDX context (not usable in unit tests).
     */
    public void loadFromFile() {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/refining_recipes.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String recipeId = entry.getString("recipeId");
            String name = entry.getString("name");
            RecipeCategory category = RecipeCategory.valueOf(entry.getString("category"));
            int requiredTier = entry.getInt("requiredTier");
            float processingTime = entry.getFloat("processingTime");
            float powerCost = entry.getFloat("powerCost");

            List<RecipeInput> inputs = new ArrayList<>();
            for (JsonValue inputVal = entry.get("inputs").child; inputVal != null; inputVal = inputVal.next) {
                inputs.add(new RecipeInput(
                    inputVal.getString("materialId"),
                    inputVal.getInt("quantity")));
            }

            List<RecipeOutput> outputs = new ArrayList<>();
            for (JsonValue outputVal = entry.get("outputs").child; outputVal != null; outputVal = outputVal.next) {
                outputs.add(new RecipeOutput(
                    outputVal.getString("materialId"),
                    outputVal.getInt("baseQuantity")));
            }

            register(new RefiningRecipe(recipeId, name, category, requiredTier,
                inputs, outputs, processingTime, powerCost));
        }
    }
}
