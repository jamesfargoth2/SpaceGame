package com.galacticodyssey.server.zone;

import java.util.List;
import java.util.UUID;

public record ZoneDefinition(
        UUID zoneId,
        String name,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        List<UUID> adjacentZoneIds,
        double boundaryOverlap
) {
    public ZoneDefinition {
        adjacentZoneIds = List.copyOf(adjacentZoneIds);
    }

    public boolean containsPoint(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean isInBoundaryOverlap(double x, double y, double z) {
        if (!containsPoint(x, y, z)) return false;
        return (x - minX) < boundaryOverlap || (maxX - x) < boundaryOverlap
                || (y - minY) < boundaryOverlap || (maxY - y) < boundaryOverlap
                || (z - minZ) < boundaryOverlap || (maxZ - z) < boundaryOverlap;
    }
}
