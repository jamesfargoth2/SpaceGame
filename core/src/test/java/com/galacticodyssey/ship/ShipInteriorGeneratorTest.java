package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipInteriorGeneratorTest {

    private HullGeometry generateHull(ShipSizeClass sizeClass) {
        return new ShipHullGenerator().generate(new ShipBlueprint(42L, sizeClass));
    }

    @Test
    void smallShipHasCockpitAndCorridor() {
        HullGeometry hull = generateHull(ShipSizeClass.SMALL);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        InteriorLayout layout = new ShipInteriorGenerator().generate(bp, hull);
        assertTrue(layout.rooms.stream().anyMatch(r -> r.type == RoomType.COCKPIT));
    }

    @Test
    void mediumShipHasEngineRoom() {
        HullGeometry hull = generateHull(ShipSizeClass.MEDIUM);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.MEDIUM);
        InteriorLayout layout = new ShipInteriorGenerator().generate(bp, hull);
        assertTrue(layout.rooms.stream().anyMatch(r -> r.type == RoomType.COCKPIT));
        assertTrue(layout.rooms.stream().anyMatch(r -> r.type == RoomType.ENGINE_ROOM));
    }

    @Test
    void interiorHasAirlockAndPilotSeat() {
        HullGeometry hull = generateHull(ShipSizeClass.SMALL);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        InteriorLayout layout = new ShipInteriorGenerator().generate(bp, hull);
        assertNotNull(layout.airlockPosition);
        assertNotNull(layout.pilotSeatPosition);
    }

    @Test
    void generatesFloorAndWallMeshData() {
        HullGeometry hull = generateHull(ShipSizeClass.SMALL);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        InteriorLayout layout = new ShipInteriorGenerator().generate(bp, hull);
        assertTrue(layout.floorVertices.length > 0);
        assertTrue(layout.floorIndices.length > 0);
        assertTrue(layout.wallVertices.length > 0);
        assertTrue(layout.wallIndices.length > 0);
    }

    @Test
    void sameSeedProducesSameLayout() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.MEDIUM);
        HullGeometry hull = new ShipHullGenerator().generate(bp);
        ShipInteriorGenerator gen = new ShipInteriorGenerator();
        InteriorLayout a = gen.generate(bp, hull);
        InteriorLayout b = gen.generate(bp, hull);
        assertEquals(a.rooms.size(), b.rooms.size());
        assertEquals(a.pilotSeatPosition, b.pilotSeatPosition);
    }

    @Test
    void largeShipHasMoreRoomsThanSmall() {
        ShipInteriorGenerator gen = new ShipInteriorGenerator();
        ShipBlueprint smallBp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        InteriorLayout smallLayout = gen.generate(smallBp, new ShipHullGenerator().generate(smallBp));
        ShipBlueprint largeBp = new ShipBlueprint(42L, ShipSizeClass.LARGE);
        InteriorLayout largeLayout = gen.generate(largeBp, new ShipHullGenerator().generate(largeBp));
        assertTrue(largeLayout.rooms.size() > smallLayout.rooms.size(),
            "Large=" + largeLayout.rooms.size() + " Small=" + smallLayout.rooms.size());
    }
}
