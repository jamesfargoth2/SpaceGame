package com.galacticodyssey.networking.components;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GhostComponentTest {
    @Test
    void storesOwningZoneId() {
        GhostComponent ghost = new GhostComponent();
        UUID zoneId = UUID.randomUUID();
        ghost.owningZoneId = zoneId;
        assertEquals(zoneId, ghost.owningZoneId);
    }

    @Test
    void defaultsToNullOwningZone() {
        GhostComponent ghost = new GhostComponent();
        assertNull(ghost.owningZoneId);
        assertFalse(ghost.readOnly);
    }

    @Test
    void readOnlyFlagCanBeSet() {
        GhostComponent ghost = new GhostComponent();
        ghost.readOnly = true;
        assertTrue(ghost.readOnly);
    }
}
