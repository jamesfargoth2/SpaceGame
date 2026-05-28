package com.galacticodyssey.crafting;

import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialRegistryTest {

    private MaterialRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaterialRegistry();
        registry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99,
            "Unprocessed iron-bearing rock", "iron_ore"));
        registry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99,
            "Purified iron compound", null));
        registry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50,
            "Smelted iron bar", null));
        registry.register(new MaterialDefinition("carbon_deposit", "Carbon Deposit",
            MaterialTier.RAW, MaterialCategory.MINERAL, 1.8f, 1.2f, 99,
            "Raw carbon material", "carbon"));
    }

    @Test
    void get_existingId_returnsMaterial() {
        MaterialDefinition def = registry.get("iron_ore");
        assertNotNull(def);
        assertEquals("Iron Ore", def.name);
        assertEquals(MaterialTier.RAW, def.tier);
        assertEquals(MaterialCategory.METAL, def.category);
    }

    @Test
    void get_unknownId_returnsNull() {
        assertNull(registry.get("unobtanium"));
    }

    @Test
    void getByTier_returnsCorrectMaterials() {
        assertEquals(2, registry.getByTier(MaterialTier.RAW).size());
        assertEquals(1, registry.getByTier(MaterialTier.PROCESSED).size());
        assertEquals(1, registry.getByTier(MaterialTier.REFINED).size());
    }

    @Test
    void getByCategory_returnsCorrectMaterials() {
        assertEquals(3, registry.getByCategory(MaterialCategory.METAL).size());
        assertEquals(1, registry.getByCategory(MaterialCategory.MINERAL).size());
        assertEquals(0, registry.getByCategory(MaterialCategory.EXOTIC).size());
    }

    @Test
    void getAll_returnsAll() {
        assertEquals(4, registry.getAll().size());
    }

    @Test
    void register_duplicateId_throwsException() {
        MaterialDefinition dupe = new MaterialDefinition("iron_ore", "Dupe",
            MaterialTier.RAW, MaterialCategory.METAL, 1.0f, 1.0f, 99, "", null);
        assertThrows(IllegalArgumentException.class, () -> registry.register(dupe));
    }
}
