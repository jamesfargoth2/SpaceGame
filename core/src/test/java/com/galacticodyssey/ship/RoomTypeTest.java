package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomTypeTest {
    @Test
    void vehicleBayExistsWithValidBounds() {
        RoomType bay = RoomType.valueOf("VEHICLE_BAY");
        assertTrue(bay.maxSizeX >= bay.minSizeX);
        assertTrue(bay.maxSizeZ >= bay.minSizeZ);
        assertTrue(bay.maxSizeY >= bay.minSizeY);
        assertNotNull(bay.floorColor);
        assertNotNull(bay.accentColor);
    }
}
