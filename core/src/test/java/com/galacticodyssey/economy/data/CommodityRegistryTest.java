package com.galacticodyssey.economy.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommodityRegistryTest {
    private CommodityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommodityRegistry();
    }

    @Test
    void registerAndGetById() {
        CommodityDefinition iron = makeCommodity("iron_ore", "Iron Ore",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 15, 2.0f, 1.5f);
        registry.register(iron);

        CommodityDefinition result = registry.get("iron_ore");
        assertNotNull(result);
        assertEquals("Iron Ore", result.name);
        assertEquals(CommodityCategory.RAW_MATERIAL, result.category);
        assertEquals(15, result.basePrice);
    }

    @Test
    void getReturnsNullForUnknownId() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void getByCategory() {
        registry.register(makeCommodity("iron_ore", "Iron Ore",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 15, 2.0f, 1.5f));
        registry.register(makeCommodity("food", "Food Rations",
                CommodityCategory.CONSUMABLE, CommodityTier.COMMON, 20, 1.0f, 1.0f));
        registry.register(makeCommodity("copper", "Copper",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 12, 2.5f, 1.5f));

        List<CommodityDefinition> rawMaterials = registry.getByCategory(CommodityCategory.RAW_MATERIAL);
        assertEquals(2, rawMaterials.size());
        assertTrue(rawMaterials.stream().allMatch(c -> c.category == CommodityCategory.RAW_MATERIAL));
    }

    @Test
    void getByTier() {
        registry.register(makeCommodity("iron_ore", "Iron Ore",
                CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 15, 2.0f, 1.5f));
        registry.register(makeCommodity("quantum_foam", "Quantum Foam",
                CommodityCategory.TECHNOLOGY, CommodityTier.EXOTIC, 10000, 0.1f, 0.1f));

        List<CommodityDefinition> common = registry.getByTier(CommodityTier.COMMON);
        assertEquals(1, common.size());
        assertEquals("iron_ore", common.get(0).id);
    }

    @Test
    void getAllReturnsAllRegistered() {
        registry.register(makeCommodity("a", "A", CommodityCategory.RAW_MATERIAL, CommodityTier.COMMON, 10, 1f, 1f));
        registry.register(makeCommodity("b", "B", CommodityCategory.LUXURY, CommodityTier.RARE, 500, 1f, 1f));

        List<CommodityDefinition> all = registry.getAll();
        assertEquals(2, all.size());
    }

    private CommodityDefinition makeCommodity(String id, String name, CommodityCategory category,
                                               CommodityTier tier, int basePrice, float mass, float volume) {
        CommodityDefinition def = new CommodityDefinition();
        def.id = id;
        def.name = name;
        def.category = category;
        def.tier = tier;
        def.basePrice = basePrice;
        def.mass = mass;
        def.volume = volume;
        return def;
    }
}
