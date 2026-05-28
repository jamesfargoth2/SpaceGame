package com.galacticodyssey.ui.actors;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.items.ConsumableItem;
import com.galacticodyssey.equipment.items.Item;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryGridActorTest {

    @Test
    void cellIndexConversion() {
        assertEquals(0, InventoryGridActor.cellIndex(0, 0, 10));
        assertEquals(15, InventoryGridActor.cellIndex(5, 1, 10));
        assertEquals(9, InventoryGridActor.cellIndex(9, 0, 10));
    }

    @Test
    void gridCoordsFromCellIndex() {
        int[] coords = InventoryGridActor.cellCoords(15, 10);
        assertEquals(5, coords[0]);
        assertEquals(1, coords[1]);
    }

    @Test
    void isLeftPanel() {
        assertTrue(InventoryGridActor.isLeftPanel(0, 10));
        assertTrue(InventoryGridActor.isLeftPanel(4, 10));
        assertFalse(InventoryGridActor.isLeftPanel(5, 10));
        assertFalse(InventoryGridActor.isLeftPanel(9, 10));
    }

    @Test
    void buildCellDataFromInventory() {
        InventoryComponent inv = new InventoryComponent(10, 6, 100f);
        Item medkit = new ConsumableItem("medkit", "Medkit", "", "", QualityTier.COMMON,
            0.5f, 50f, "", 1f, 5);
        inv.tryAdd(medkit);

        Item found = inv.getItemAt(0, 0);
        assertNotNull(found);
        assertEquals("medkit", found.id);
    }
}
