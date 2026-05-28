package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipDesignTest {

    @Test
    void fromSeed_populatesHullFromBlueprint() {
        ShipDesign design = ShipDesign.fromSeed(42L, ShipSizeClass.MEDIUM);
        assertEquals(ShipSizeClass.MEDIUM, design.sizeClass);
        assertEquals(4, design.hull.spinePoints.size());
        assertTrue(design.hull.crossSections.size() >= 8);
        assertTrue(design.hull.estimateSpineLength() > 0);
    }

    @Test
    void toBlueprint_producesValidBlueprint() {
        ShipDesign design = ShipDesign.fromSeed(42L, ShipSizeClass.SMALL);
        ShipBlueprint bp = design.toBlueprint();
        assertEquals(ShipSizeClass.SMALL, bp.sizeClass);
        assertTrue(bp.spineLength > 0);
        assertTrue(bp.crossSectionCount >= 2);
    }

    @Test
    void addRoom_incrementsRoomList() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        assertEquals(1, design.rooms.size());
    }

    @Test
    void totalRoomVolume_sumsAllRooms() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 5, 0, 0, 3, 3, 3));
        assertEquals(36 + 27, design.totalRoomVolume());
    }

    @Test
    void setModule_storesAssignment() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        assertNotNull(design.modules.get("WPN-1"));
        assertEquals("laser_mk1", design.modules.get("WPN-1").moduleId);
    }

    @Test
    void clearModule_removesAssignment() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        design.clearModule("WPN-1");
        assertNull(design.modules.get("WPN-1"));
    }
}
