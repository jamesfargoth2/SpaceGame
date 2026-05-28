package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.shipbuilder.planning.BuildAction;
import com.galacticodyssey.shipbuilder.planning.BuildOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildOrderTest {
    private BuildOrder order;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        order = new BuildOrder();
        design = new ShipDesign(ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
    }

    @Test
    void totalCost_sumsAllActions() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.addAction(BuildAction.swapModule("WPN-1",
            new ModuleAssignment("laser_mk2", "bp_laser_mk2"), "Laser Mk2", 8500));
        assertEquals(20500, order.totalCost());
    }

    @Test
    void totalCost_subtractsRefunds() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.addAction(BuildAction.removeRoom(1, "ENGINE_ROOM", 5000));
        assertEquals(12000 - 5000, order.totalCost());
    }

    @Test
    void applyTo_addsRoom() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.applyTo(design);
        assertEquals(3, design.rooms.size());
        assertEquals(RoomType.MEDBAY, design.rooms.get(2).type);
    }

    @Test
    void applyTo_swapsModule() {
        order.addAction(BuildAction.swapModule("WPN-1",
            new ModuleAssignment("laser_mk2", "bp_laser_mk2"), "Laser Mk2", 8500));
        order.applyTo(design);
        assertNotNull(design.modules.get("WPN-1"));
        assertEquals("laser_mk2", design.modules.get("WPN-1").moduleId);
    }

    @Test
    void removeAction_updatesTotalCost() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.addAction(BuildAction.swapModule("WPN-1",
            new ModuleAssignment("laser_mk2", "bp_laser_mk2"), "Laser Mk2", 8500));
        order.removeAction(0);
        assertEquals(8500, order.totalCost());
    }

    @Test
    void clear_emptiesQueue() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.clear();
        assertTrue(order.isEmpty());
        assertEquals(0, order.totalCost());
    }
}
