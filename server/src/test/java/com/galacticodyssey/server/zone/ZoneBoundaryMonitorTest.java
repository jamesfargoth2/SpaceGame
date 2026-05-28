package com.galacticodyssey.server.zone;

import com.galacticodyssey.server.network.PlayerSession;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ZoneBoundaryMonitorTest {

    private ZoneDefinition makeZone(double minX, double maxX) {
        return new ZoneDefinition(UUID.randomUUID(), "test-zone", minX, 0, 0, maxX, 1000, 1000, List.of(), 100.0);
    }

    @Test
    void detectsPlayerInBoundaryOverlap() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(950, 500, 500);
        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertEquals(1, events.size());
        assertEquals(ZoneBoundaryMonitor.BoundaryEventType.ENTERED_OVERLAP, events.get(0).type());
    }

    @Test
    void noEventWhenPlayerInCenter() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(500, 500, 500);
        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertTrue(events.isEmpty());
    }

    @Test
    void detectsPlayerLeftOverlap() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(950, 500, 500);
        monitor.check(List.of(session));
        session.setGalaxyPosition(500, 500, 500);
        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertEquals(1, events.size());
        assertEquals(ZoneBoundaryMonitor.BoundaryEventType.LEFT_OVERLAP, events.get(0).type());
    }

    @Test
    void detectsPlayerExitedZone() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(1100, 500, 500);
        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertEquals(1, events.size());
        assertEquals(ZoneBoundaryMonitor.BoundaryEventType.EXITED_ZONE, events.get(0).type());
    }
}
