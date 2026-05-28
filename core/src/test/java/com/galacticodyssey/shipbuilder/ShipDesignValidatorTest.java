package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShipDesignValidatorTest {
    private ShipDesignValidator validator;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        validator = new ShipDesignValidator();
        design = new ShipDesign(ShipSizeClass.SMALL);
    }

    @Test
    void validate_missingRequiredRooms() {
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("MISSING_COCKPIT")));
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("MISSING_ENGINE_ROOM")));
    }

    @Test
    void validate_noErrorsWithRequiredRooms() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_detectsOverlap() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 3, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("ROOM_OVERLAP")));
    }

    @Test
    void validate_detectsDisconnectedRooms() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 20, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("DISCONNECTED_ROOMS")));
    }

    @Test
    void validate_roomOutsideHull() {
        boolean[][][] mask = new boolean[10][5][5];
        for (int x = 0; x < 10; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++)
                    mask[x][y][z] = true;
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 8, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, mask);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("ROOM_OUTSIDE_HULL")));
    }

    @Test
    void canPlaceRoom_rejectsOverlap() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        RoomDesign candidate = new RoomDesign(RoomType.MEDBAY, 2, 0, 0, 3, 3, 3);
        assertFalse(validator.canPlaceRoom(design, candidate, null));
    }

    @Test
    void canPlaceRoom_acceptsAdjacentRoom() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        RoomDesign candidate = new RoomDesign(RoomType.MEDBAY, 4, 0, 0, 3, 3, 3);
        assertTrue(validator.canPlaceRoom(design, candidate, null));
    }

    @Test
    void canPlaceRoom_firstRoomAlwaysValid() {
        RoomDesign candidate = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        assertTrue(validator.canPlaceRoom(design, candidate, null));
    }

    @Test
    void areAdjacent_touchingOnXAxis() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 4, 0, 0, 3, 3, 3);
        assertTrue(validator.areAdjacent(a, b));
    }

    @Test
    void areAdjacent_notTouching() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 5, 0, 0, 3, 3, 3);
        assertFalse(validator.areAdjacent(a, b));
    }
}
