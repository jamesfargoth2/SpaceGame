package com.galacticodyssey.crafting;

import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.persistence.snapshots.MaterialStorageSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialStorageComponentTest {

    private MaterialStorageComponent storage;
    private MaterialRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaterialRegistry();
        registry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        registry.register(new MaterialDefinition("copper_ore", "Copper Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.2f, 1.0f, 99, "", "copper"));
        registry.register(new MaterialDefinition("carbon_deposit", "Carbon Deposit",
            MaterialTier.RAW, MaterialCategory.MINERAL, 1.8f, 1.2f, 99, "", "carbon"));
        storage = new MaterialStorageComponent(100f, 80f, registry);
    }

    @Test
    void tryAdd_withinCapacity_succeeds() {
        assertTrue(storage.tryAdd("iron_ore", 10));
        assertEquals(10, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryAdd_exceedsWeightCapacity_fails() {
        assertFalse(storage.tryAdd("iron_ore", 51));
        assertEquals(0, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryAdd_exceedsVolumeCapacity_fails() {
        assertFalse(storage.tryAdd("carbon_deposit", 81));
        assertEquals(0, storage.getQuantity("carbon_deposit"));
    }

    @Test
    void tryAdd_multipleMaterials_tracksIndependently() {
        storage.tryAdd("iron_ore", 5);
        storage.tryAdd("copper_ore", 3);
        assertEquals(5, storage.getQuantity("iron_ore"));
        assertEquals(3, storage.getQuantity("copper_ore"));
    }

    @Test
    void tryAdd_unknownMaterial_fails() {
        assertFalse(storage.tryAdd("unobtanium", 1));
    }

    @Test
    void hasEnough_sufficient_returnsTrue() {
        storage.tryAdd("iron_ore", 10);
        assertTrue(storage.hasEnough("iron_ore", 5));
        assertTrue(storage.hasEnough("iron_ore", 10));
    }

    @Test
    void hasEnough_insufficient_returnsFalse() {
        storage.tryAdd("iron_ore", 3);
        assertFalse(storage.hasEnough("iron_ore", 5));
    }

    @Test
    void hasEnough_noStock_returnsFalse() {
        assertFalse(storage.hasEnough("iron_ore", 1));
    }

    @Test
    void tryConsume_sufficient_removesAndReturnsTrue() {
        storage.tryAdd("iron_ore", 10);
        assertTrue(storage.tryConsume("iron_ore", 4));
        assertEquals(6, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryConsume_exactAmount_removesEntry() {
        storage.tryAdd("iron_ore", 5);
        assertTrue(storage.tryConsume("iron_ore", 5));
        assertEquals(0, storage.getQuantity("iron_ore"));
    }

    @Test
    void tryConsume_insufficient_doesNothingAndReturnsFalse() {
        storage.tryAdd("iron_ore", 3);
        assertFalse(storage.tryConsume("iron_ore", 5));
        assertEquals(3, storage.getQuantity("iron_ore"));
    }

    @Test
    void getCurrentWeight_sumsCorrectly() {
        storage.tryAdd("iron_ore", 10);
        storage.tryAdd("copper_ore", 5);
        assertEquals(31.0f, storage.getCurrentWeight(), 0.01f);
    }

    @Test
    void getCurrentVolume_sumsCorrectly() {
        storage.tryAdd("iron_ore", 10);
        storage.tryAdd("carbon_deposit", 5);
        assertEquals(16.0f, storage.getCurrentVolume(), 0.01f);
    }

    @Test
    void snapshotRoundTrip_preservesState() {
        storage.tryAdd("iron_ore", 15);
        storage.tryAdd("copper_ore", 7);

        MaterialStorageSnapshot snapshot = storage.takeSnapshot();
        assertEquals(2, snapshot.quantities.size());
        assertEquals(15, snapshot.quantities.get("iron_ore"));

        MaterialStorageComponent restored = new MaterialStorageComponent(100f, 80f, registry);
        restored.restoreFromSnapshot(snapshot);
        assertEquals(15, restored.getQuantity("iron_ore"));
        assertEquals(7, restored.getQuantity("copper_ore"));
    }
}
