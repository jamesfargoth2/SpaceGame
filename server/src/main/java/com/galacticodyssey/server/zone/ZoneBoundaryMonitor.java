package com.galacticodyssey.server.zone;

import com.galacticodyssey.server.network.PlayerSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoneBoundaryMonitor {

    public enum BoundaryEventType {
        ENTERED_OVERLAP,
        LEFT_OVERLAP,
        EXITED_ZONE
    }

    public record BoundaryEvent(int connectionId, BoundaryEventType type) {}

    private final ZoneDefinition zone;
    private final Map<Integer, Boolean> previouslyInOverlap = new HashMap<>();

    public ZoneBoundaryMonitor(ZoneDefinition zone) {
        this.zone = zone;
    }

    public List<BoundaryEvent> check(List<PlayerSession> sessions) {
        List<BoundaryEvent> events = new ArrayList<>();
        for (PlayerSession session : sessions) {
            double x = session.getGalaxyX();
            double y = session.getGalaxyY();
            double z = session.getGalaxyZ();
            int connId = session.getConnectionId();

            boolean inZone = zone.containsPoint(x, y, z);
            boolean inOverlap = inZone && zone.isInBoundaryOverlap(x, y, z);
            boolean wasInOverlap = previouslyInOverlap.getOrDefault(connId, false);

            if (!inZone) {
                events.add(new BoundaryEvent(connId, BoundaryEventType.EXITED_ZONE));
                previouslyInOverlap.remove(connId);
            } else if (inOverlap && !wasInOverlap) {
                events.add(new BoundaryEvent(connId, BoundaryEventType.ENTERED_OVERLAP));
                previouslyInOverlap.put(connId, true);
            } else if (!inOverlap && wasInOverlap) {
                events.add(new BoundaryEvent(connId, BoundaryEventType.LEFT_OVERLAP));
                previouslyInOverlap.put(connId, false);
            }
        }
        return events;
    }

    public void removePlayer(int connectionId) {
        previouslyInOverlap.remove(connectionId);
    }
}
