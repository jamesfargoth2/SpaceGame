package com.galacticodyssey.crafting;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.equipment.items.MaterialItem;
import com.galacticodyssey.persistence.snapshots.ItemSnapshot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialItemTest {

    @Test
    void getType_returnsMaterial() {
        MaterialItem item = createIronOre();
        assertEquals(ItemType.MATERIAL, item.getType());
    }

    @Test
    void materialFields_returnCorrectValues() {
        MaterialItem item = createIronOre();
        assertEquals("iron_ore", item.getMaterialId());
        assertEquals(MaterialTier.RAW, item.getTier());
        assertEquals(MaterialCategory.METAL, item.getCategory());
        assertEquals("iron_ore", item.getCommodityLink());
    }

    @Test
    void snapshotRoundTrip_preservesAllFields() {
        MaterialItem original = createIronOre();
        original.currentStack = 15;

        ItemSnapshot snapshot = original.toItemSnapshot();
        assertEquals("MATERIAL", snapshot.itemType);

        Item restored = Item.fromItemSnapshot(snapshot);
        assertInstanceOf(MaterialItem.class, restored);

        MaterialItem mat = (MaterialItem) restored;
        assertEquals("iron_ore", mat.getMaterialId());
        assertEquals(MaterialTier.RAW, mat.getTier());
        assertEquals(MaterialCategory.METAL, mat.getCategory());
        assertEquals("iron_ore", mat.getCommodityLink());
        assertEquals(15, mat.currentStack);
    }

    @Test
    void snapshotRoundTrip_nullCommodityLink_preserved() {
        MaterialItem item = new MaterialItem("iron_concentrate", "Iron Concentrate",
            "Purified iron compound", "iron_conc_icon", QualityTier.COMMON,
            1.5f, 99, "iron_concentrate", MaterialTier.PROCESSED,
            MaterialCategory.METAL, null);

        ItemSnapshot snapshot = item.toItemSnapshot();
        MaterialItem restored = (MaterialItem) Item.fromItemSnapshot(snapshot);
        assertNull(restored.getCommodityLink());
    }

    @Test
    void canStackWith_sameMaterial_returnsTrue() {
        MaterialItem a = createIronOre();
        a.currentStack = 10;
        MaterialItem b = createIronOre();
        b.currentStack = 5;
        assertTrue(a.canStackWith(b));
    }

    @Test
    void canStackWith_differentMaterial_returnsFalse() {
        MaterialItem iron = createIronOre();
        MaterialItem copper = new MaterialItem("copper_ore", "Copper Ore", "Raw copper",
            "copper_icon", QualityTier.COMMON, 2.0f, 99,
            "copper_ore", MaterialTier.RAW, MaterialCategory.METAL, "copper_ore");
        assertFalse(iron.canStackWith(copper));
    }

    private MaterialItem createIronOre() {
        return new MaterialItem("iron_ore", "Iron Ore", "Unprocessed iron-bearing rock",
            "iron_ore_icon", QualityTier.COMMON, 2.0f, 99,
            "iron_ore", MaterialTier.RAW, MaterialCategory.METAL, "iron_ore");
    }
}
