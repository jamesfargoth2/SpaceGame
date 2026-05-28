package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomDesignTest {

    @Test
    void volume_computesCorrectly() {
        RoomDesign room = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        assertEquals(36, room.volume());
    }

    @Test
    void overlaps_detectsOverlap() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 3, 0, 0, 3, 3, 3);
        assertTrue(a.overlaps(b));
    }

    @Test
    void overlaps_returnsFalseForAdjacentRooms() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 4, 0, 0, 3, 3, 3);
        assertFalse(a.overlaps(b));
    }

    @Test
    void containsCell_checksBounds() {
        RoomDesign room = new RoomDesign(RoomType.CARGO_BAY, 5, 0, 2, 4, 3, 3);
        assertTrue(room.containsCell(5, 0, 2));
        assertTrue(room.containsCell(8, 2, 4));
        assertFalse(room.containsCell(9, 0, 2));
        assertFalse(room.containsCell(4, 0, 2));
    }

    @Test
    void copy_isIndependent() {
        RoomDesign original = new RoomDesign(RoomType.ARMORY, 1, 2, 3, 3, 3, 3);
        RoomDesign copy = original.copy();
        copy.gridX = 99;
        assertEquals(1, original.gridX);
    }
}
