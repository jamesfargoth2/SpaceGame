package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;

import java.util.*;

public class ShipDesignValidator {

    public static class ValidationError {
        public final String code;
        public final String message;
        public ValidationError(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public List<ValidationError> validate(ShipDesign design, boolean[][][] hullMask) {
        List<ValidationError> errors = new ArrayList<>();
        validateRequiredRooms(design, errors);
        validateNoOverlaps(design, errors);
        if (hullMask != null) {
            validateRoomsFitInHull(design, hullMask, errors);
        }
        validateConnectivity(design, errors);
        return errors;
    }

    private void validateRequiredRooms(ShipDesign design, List<ValidationError> errors) {
        boolean hasCockpit = false, hasEngine = false;
        for (RoomDesign r : design.rooms) {
            if (r.type == RoomType.COCKPIT) hasCockpit = true;
            if (r.type == RoomType.ENGINE_ROOM) hasEngine = true;
        }
        if (!hasCockpit) errors.add(new ValidationError("MISSING_COCKPIT", "Cockpit is required"));
        if (!hasEngine) errors.add(new ValidationError("MISSING_ENGINE_ROOM", "Engine Room is required"));
    }

    private void validateNoOverlaps(ShipDesign design, List<ValidationError> errors) {
        for (int i = 0; i < design.rooms.size(); i++) {
            for (int j = i + 1; j < design.rooms.size(); j++) {
                if (design.rooms.get(i).overlaps(design.rooms.get(j))) {
                    errors.add(new ValidationError("ROOM_OVERLAP",
                        "Room " + design.rooms.get(i).type + " overlaps " + design.rooms.get(j).type));
                }
            }
        }
    }

    private void validateRoomsFitInHull(ShipDesign design, boolean[][][] hullMask, List<ValidationError> errors) {
        int maxX = hullMask.length;
        int maxY = hullMask[0].length;
        int maxZ = hullMask[0][0].length;
        for (RoomDesign room : design.rooms) {
            for (int x = room.gridX; x < room.gridX + room.sizeX; x++) {
                for (int y = room.gridY; y < room.gridY + room.sizeY; y++) {
                    for (int z = room.gridZ; z < room.gridZ + room.sizeZ; z++) {
                        if (x < 0 || x >= maxX || y < 0 || y >= maxY || z < 0 || z >= maxZ
                            || !hullMask[x][y][z]) {
                            errors.add(new ValidationError("ROOM_OUTSIDE_HULL",
                                room.type + " extends outside hull at (" + x + "," + y + "," + z + ")"));
                            return;
                        }
                    }
                }
            }
        }
    }

    private void validateConnectivity(ShipDesign design, List<ValidationError> errors) {
        if (design.rooms.size() <= 1) return;
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        visited.add(0);
        queue.add(0);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            RoomDesign currentRoom = design.rooms.get(current);
            for (int i = 0; i < design.rooms.size(); i++) {
                if (visited.contains(i)) continue;
                if (areAdjacent(currentRoom, design.rooms.get(i))) {
                    visited.add(i);
                    queue.add(i);
                }
            }
        }
        if (visited.size() < design.rooms.size()) {
            errors.add(new ValidationError("DISCONNECTED_ROOMS", "Not all rooms are reachable"));
        }
    }

    public boolean areAdjacent(RoomDesign a, RoomDesign b) {
        boolean xOverlap = a.gridX < b.gridX + b.sizeX && a.gridX + a.sizeX > b.gridX;
        boolean yOverlap = a.gridY < b.gridY + b.sizeY && a.gridY + a.sizeY > b.gridY;
        boolean zOverlap = a.gridZ < b.gridZ + b.sizeZ && a.gridZ + a.sizeZ > b.gridZ;

        boolean xTouching = (a.gridX + a.sizeX == b.gridX || b.gridX + b.sizeX == a.gridX);
        boolean yTouching = (a.gridY + a.sizeY == b.gridY || b.gridY + b.sizeY == a.gridY);
        boolean zTouching = (a.gridZ + a.sizeZ == b.gridZ || b.gridZ + b.sizeZ == a.gridZ);

        return (xTouching && yOverlap && zOverlap)
            || (yTouching && xOverlap && zOverlap)
            || (zTouching && xOverlap && yOverlap);
    }

    public boolean canPlaceRoom(ShipDesign design, RoomDesign candidate, boolean[][][] hullMask) {
        for (RoomDesign existing : design.rooms) {
            if (candidate.overlaps(existing)) return false;
        }
        if (hullMask != null) {
            int maxX = hullMask.length, maxY = hullMask[0].length, maxZ = hullMask[0][0].length;
            for (int x = candidate.gridX; x < candidate.gridX + candidate.sizeX; x++) {
                for (int y = candidate.gridY; y < candidate.gridY + candidate.sizeY; y++) {
                    for (int z = candidate.gridZ; z < candidate.gridZ + candidate.sizeZ; z++) {
                        if (x < 0 || x >= maxX || y < 0 || y >= maxY || z < 0 || z >= maxZ
                            || !hullMask[x][y][z]) {
                            return false;
                        }
                    }
                }
            }
        }
        if (!design.rooms.isEmpty()) {
            boolean adjacentToAny = false;
            for (RoomDesign existing : design.rooms) {
                if (areAdjacent(candidate, existing)) { adjacentToAny = true; break; }
            }
            if (!adjacentToAny) return false;
        }
        return true;
    }
}
