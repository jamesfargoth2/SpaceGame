package com.galacticodyssey.server.zone;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ZoneDefinitionTest {
    @Test
    void containsPointInsideBounds() {
        ZoneDefinition zone = new ZoneDefinition(UUID.randomUUID(), "zone-alpha", 0, 0, 0, 1000, 1000, 1000, List.of(), 100.0);
        assertTrue(zone.containsPoint(500, 500, 500));
    }

    @Test
    void rejectsPointOutsideBounds() {
        ZoneDefinition zone = new ZoneDefinition(UUID.randomUUID(), "zone-alpha", 0, 0, 0, 1000, 1000, 1000, List.of(), 100.0);
        assertFalse(zone.containsPoint(1500, 500, 500));
    }

    @Test
    void pointInBoundaryOverlap() {
        ZoneDefinition zone = new ZoneDefinition(UUID.randomUUID(), "zone-alpha", 0, 0, 0, 1000, 1000, 1000, List.of(), 100.0);
        assertTrue(zone.isInBoundaryOverlap(950, 500, 500));
        assertFalse(zone.isInBoundaryOverlap(500, 500, 500));
    }

    @Test
    void pointNearMinBoundaryIsOverlap() {
        ZoneDefinition zone = new ZoneDefinition(UUID.randomUUID(), "zone-alpha", 1000, 1000, 1000, 2000, 2000, 2000, List.of(), 200.0);
        assertTrue(zone.isInBoundaryOverlap(1100, 1500, 1500));
    }

    @Test
    void adjacentZonesStored() {
        UUID adj1 = UUID.randomUUID();
        UUID adj2 = UUID.randomUUID();
        ZoneDefinition zone = new ZoneDefinition(UUID.randomUUID(), "zone-alpha", 0, 0, 0, 1000, 1000, 1000, List.of(adj1, adj2), 100.0);
        assertEquals(2, zone.adjacentZoneIds().size());
        assertTrue(zone.adjacentZoneIds().contains(adj1));
    }
}
