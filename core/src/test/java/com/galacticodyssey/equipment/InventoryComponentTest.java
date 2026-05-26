package com.galacticodyssey.equipment;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.items.AmmoItem;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.equipment.items.JunkItem;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.combat.data.WeaponAssembly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryComponentTest {

    private InventoryComponent inventory;

    @BeforeEach
    void setUp() {
        inventory = new InventoryComponent(8, 6, 50f);
    }

    @Test
    void addSmallItem_placedAtFirstAvailableSlot() {
        AmmoItem ammo = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        assertTrue(inventory.tryAdd(ammo));
        assertSame(ammo, inventory.getItemAt(0, 0));
    }

    @Test
    void addLargeItem_occupiesMultipleCells() {
        WeaponAssembly assembly = WeaponAssembly.ranged("rifle_standard", "long_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem rifle = new WeaponItem("rifle_1", "Standard Rifle", "A rifle",
            "rifle_icon", QualityTier.COMMON, 3, 1, 3.5f, assembly);
        assertTrue(inventory.tryAdd(rifle));
        assertSame(rifle, inventory.getItemAt(0, 0));
        assertSame(rifle, inventory.getItemAt(1, 0));
        assertSame(rifle, inventory.getItemAt(2, 0));
        assertNull(inventory.getItemAt(3, 0));
    }

    @Test
    void addItem_exceedsWeight_rejected() {
        inventory = new InventoryComponent(8, 6, 1f);
        WeaponAssembly assembly = WeaponAssembly.ranged("rifle_standard", "long_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem heavy = new WeaponItem("heavy_1", "Heavy Gun", "Too heavy",
            "heavy_icon", QualityTier.COMMON, 2, 1, 5.0f, assembly);
        assertFalse(inventory.tryAdd(heavy));
    }

    @Test
    void addStackableItem_mergesWithExisting() {
        AmmoItem ammo1 = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        ammo1.currentStack = 30;
        AmmoItem ammo2 = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        ammo2.currentStack = 20;

        assertTrue(inventory.tryAdd(ammo1));
        assertTrue(inventory.tryAdd(ammo2));
        assertEquals(50, ammo1.currentStack);
    }

    @Test
    void removeItem_freesGridCells() {
        AmmoItem ammo = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.1f, "standard_round", 60);
        inventory.tryAdd(ammo);
        assertTrue(inventory.remove(ammo));
        assertNull(inventory.getItemAt(0, 0));
    }

    @Test
    void gridFull_rejectsNewItems() {
        inventory = new InventoryComponent(1, 1, 100f);
        AmmoItem ammo1 = new AmmoItem("ammo_a", "Ammo A", "A",
            "icon", QualityTier.COMMON, 0.1f, "a", 1);
        AmmoItem ammo2 = new AmmoItem("ammo_b", "Ammo B", "B",
            "icon", QualityTier.COMMON, 0.1f, "b", 1);
        assertTrue(inventory.tryAdd(ammo1));
        assertFalse(inventory.tryAdd(ammo2));
    }

    @Test
    void getCurrentWeight_sumsAllItems() {
        AmmoItem ammo = new AmmoItem("ammo_std", "Standard Rounds", "Basic ammo",
            "ammo_icon", QualityTier.COMMON, 0.5f, "standard_round", 60);
        ammo.currentStack = 10;
        inventory.tryAdd(ammo);
        assertEquals(5.0f, inventory.getCurrentWeight(), 0.01f);
    }
}
