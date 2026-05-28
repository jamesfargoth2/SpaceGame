package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuilderPhaseControllerTest {
    private BuilderPhaseController controller;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        design = new ShipDesign(ShipSizeClass.SMALL);
        design.hull.spinePoints.add(new Vector3(0, 0, 0));
        design.hull.spinePoints.add(new Vector3(0, 0, 5));
        design.hull.spinePoints.add(new Vector3(0, 0, 10));
        design.hull.addCrossSection(new CrossSectionDef(0f, 2f, 2f, 2f));
        design.hull.addCrossSection(new CrossSectionDef(1f, 1f, 1f, 2f));

        controller = new BuilderPhaseController(design, new ShipDesignValidator(), new EventBus());
    }

    @Test
    void startsAtHullSculpt() {
        assertEquals(BuilderPhase.HULL_SCULPT, controller.getCurrentPhase());
    }

    @Test
    void advance_fromHullSculptToRoomLayout() {
        assertTrue(controller.advance());
        assertEquals(BuilderPhase.ROOM_LAYOUT, controller.getCurrentPhase());
    }

    @Test
    void advance_fromRoomLayoutRequiresValidDesign() {
        controller.advance();
        assertFalse(controller.canAdvance());
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
        assertTrue(controller.canAdvance());
        assertTrue(controller.advance());
        assertEquals(BuilderPhase.MODULE_FIT, controller.getCurrentPhase());
    }

    @Test
    void goBack_fromModuleFitToRoomLayout() {
        controller.advance();
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
        controller.advance();
        assertTrue(controller.goBack());
        assertEquals(BuilderPhase.ROOM_LAYOUT, controller.getCurrentPhase());
        assertTrue(controller.areModulesInvalidated());
    }

    @Test
    void goBack_fromRoomLayoutInvalidatesRooms() {
        controller.advance();
        assertTrue(controller.goBack());
        assertEquals(BuilderPhase.HULL_SCULPT, controller.getCurrentPhase());
        assertTrue(controller.areRoomsInvalidated());
        assertTrue(controller.areModulesInvalidated());
    }

    @Test
    void goBack_fromHullSculptReturnsFalse() {
        assertFalse(controller.goBack());
    }

    @Test
    void canAdvance_failsWithoutMinimumHullData() {
        ShipDesign emptyDesign = new ShipDesign(ShipSizeClass.SMALL);
        BuilderPhaseController ctrl = new BuilderPhaseController(emptyDesign, new ShipDesignValidator(), new EventBus());
        assertFalse(ctrl.canAdvance());
    }
}
