package com.galacticodyssey.crafting;

import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningRecipeRegistryTest {

    private RefiningRecipeRegistry recipeRegistry;
    private MaterialRegistry materialRegistry;

    @BeforeEach
    void setUp() {
        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50, "", null));
        materialRegistry.register(new MaterialDefinition("carbon_powder", "Carbon Powder",
            MaterialTier.PROCESSED, MaterialCategory.MINERAL, 0.8f, 0.6f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("steel_alloy", "Steel Alloy",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.2f, 0.5f, 50, "", null));

        recipeRegistry = new RefiningRecipeRegistry();
        recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Process Iron Ore",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("iron_ore", 5)),
            List.of(new RecipeOutput("iron_concentrate", 3)),
            30.0f, 10f));
        recipeRegistry.register(new RefiningRecipe("refine_iron", "Refine Iron",
            RecipeCategory.REFINEMENT, 2,
            List.of(new RecipeInput("iron_concentrate", 4)),
            List.of(new RecipeOutput("iron_ingot", 2)),
            60.0f, 20f));
        recipeRegistry.register(new RefiningRecipe("forge_steel", "Forge Steel Alloy",
            RecipeCategory.ALLOY, 3,
            List.of(new RecipeInput("iron_ingot", 3), new RecipeInput("carbon_powder", 1)),
            List.of(new RecipeOutput("steel_alloy", 2)),
            90.0f, 30f));
    }

    @Test
    void getRecipe_existingId_returnsRecipe() {
        RefiningRecipe recipe = recipeRegistry.getRecipe("process_iron_ore");
        assertNotNull(recipe);
        assertEquals("Process Iron Ore", recipe.name);
        assertEquals(RecipeCategory.PROCESSING, recipe.category);
        assertEquals(1, recipe.requiredTier);
    }

    @Test
    void getRecipe_unknownId_returnsNull() {
        assertNull(recipeRegistry.getRecipe("unknown_recipe"));
    }

    @Test
    void getRecipesForTier_returnsUpToTier() {
        assertEquals(1, recipeRegistry.getRecipesForTier(1).size());
        assertEquals(2, recipeRegistry.getRecipesForTier(2).size());
        assertEquals(3, recipeRegistry.getRecipesForTier(3).size());
    }

    @Test
    void getRecipesProducing_returnsMatchingRecipes() {
        List<RefiningRecipe> ironConc = recipeRegistry.getRecipesProducing("iron_concentrate");
        assertEquals(1, ironConc.size());
        assertEquals("process_iron_ore", ironConc.get(0).recipeId);
    }

    @Test
    void validate_allInputsAndOutputsExist_returnsTrue() {
        assertTrue(recipeRegistry.validate(materialRegistry));
    }

    @Test
    void validate_missingMaterial_returnsFalse() {
        RefiningRecipeRegistry badRegistry = new RefiningRecipeRegistry();
        badRegistry.register(new RefiningRecipe("bad_recipe", "Bad",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("nonexistent", 1)),
            List.of(new RecipeOutput("iron_concentrate", 1)),
            10f, 5f));
        assertFalse(badRegistry.validate(materialRegistry));
    }

    @Test
    void register_duplicateId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
            recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Dupe",
                RecipeCategory.PROCESSING, 1,
                List.of(new RecipeInput("iron_ore", 1)),
                List.of(new RecipeOutput("iron_concentrate", 1)),
                10f, 5f)));
    }

    @Test
    void recipeInputsAndOutputs_correctValues() {
        RefiningRecipe steel = recipeRegistry.getRecipe("forge_steel");
        assertEquals(2, steel.inputs.size());
        assertEquals("iron_ingot", steel.inputs.get(0).materialId);
        assertEquals(3, steel.inputs.get(0).quantity);
        assertEquals(1, steel.outputs.size());
        assertEquals("steel_alloy", steel.outputs.get(0).materialId);
        assertEquals(2, steel.outputs.get(0).baseQuantity);
    }
}
