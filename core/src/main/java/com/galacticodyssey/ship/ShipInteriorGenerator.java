package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import java.util.*;

/**
 * Generates an interior room layout for a procedural spaceship hull.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Voxelise the hull volume into a 3-D grid of ~1 m cells.</li>
 *   <li>Shrink the mask by one cell to leave room for hull walls.</li>
 *   <li>Build a room manifest based on ship size class.</li>
 *   <li>Greedily pack rooms: cockpit at nose (high Z), engine room at tail (low Z).</li>
 *   <li>Connect adjacent rooms with L-shaped corridor paths.</li>
 *   <li>Emit floor, wall and ceiling quad meshes for every room and corridor cell.</li>
 *   <li>Locate the airlock and pilot seat world positions.</li>
 * </ol>
 *
 * <p>Interior vertex stride = {@link #VERTEX_STRIDE} = 10 floats:
 * position(3) + normal(3) + color(4).
 */
public class ShipInteriorGenerator {

    private static final float CELL_SIZE = 1f;
    public static final int VERTEX_STRIDE = 10;

    /** Entry point: generate a full {@link InteriorLayout} from blueprint + hull mesh. */
    public InteriorLayout generate(ShipBlueprint blueprint, HullGeometry hull) {
        Random rng = new Random(blueprint.seed + 100);
        BoundingBox bbox = hull.boundingBox;

        Vector3 min = new Vector3();
        Vector3 max = new Vector3();
        bbox.getMin(min);
        bbox.getMax(max);

        int gridX = Math.max(1, (int) Math.ceil((max.x - min.x) / CELL_SIZE));
        int gridY = Math.max(1, (int) Math.ceil((max.y - min.y) / CELL_SIZE));
        int gridZ = Math.max(1, (int) Math.ceil((max.z - min.z) / CELL_SIZE));

        boolean[][][] inside   = voxelizeHull(hull, min, gridX, gridY, gridZ);
        boolean[][][] packable = shrinkMask(inside, gridX, gridY, gridZ);

        List<RoomType>      manifest = getRoomManifest(blueprint.sizeClass, rng);
        List<RoomPlacement> rooms    = packRooms(manifest, packable, gridX, gridY, gridZ, rng);

        boolean[][][] corridorCells = new boolean[gridX][gridY][gridZ];
        connectRoomsWithCorridors(rooms, corridorCells, packable, gridX, gridY, gridZ);

        Vector3 airlockPosition   = findAirlockPosition(rooms, min);
        Vector3 pilotSeatPosition = findPilotSeatPosition(rooms, min);

        List<float[]> floorVerts = new ArrayList<>();
        List<short[]> floorInds  = new ArrayList<>();
        List<float[]> wallVerts  = new ArrayList<>();
        List<short[]> wallInds   = new ArrayList<>();

        int floorVertOffset = 0;
        int wallVertOffset  = 0;

        for (RoomPlacement room : rooms) {
            floorVertOffset = generateRoomFloor(room, min, floorVerts, floorInds, floorVertOffset);
            wallVertOffset  = generateRoomWalls(room, min, wallVerts,  wallInds,  wallVertOffset);
        }

        for (int x = 0; x < gridX; x++) {
            for (int y = 0; y < gridY; y++) {
                for (int z = 0; z < gridZ; z++) {
                    if (corridorCells[x][y][z]) {
                        RoomPlacement corridor = new RoomPlacement(RoomType.CORRIDOR, x, y, z, 1, 1, 1);
                        floorVertOffset = generateRoomFloor(corridor, min, floorVerts, floorInds, floorVertOffset);
                        wallVertOffset  = generateRoomWalls(corridor, min, wallVerts,  wallInds,  wallVertOffset);
                    }
                }
            }
        }

        return new InteriorLayout(
            rooms, corridorCells, airlockPosition, pilotSeatPosition,
            mergeFloats(floorVerts), mergeShorts(floorInds),
            mergeFloats(wallVerts),  mergeShorts(wallInds),
            gridX, gridY, gridZ
        );
    }

    // -------------------------------------------------------------------------
    // Voxelisation
    // -------------------------------------------------------------------------

    /**
     * Marks each grid cell as inside/outside using an approximate elliptical
     * cross-section derived from the hull bounding box, with Z-axis taper
     * matching the nose→body→tail silhouette.
     */
    private boolean[][][] voxelizeHull(HullGeometry hull, Vector3 min,
                                       int gx, int gy, int gz) {
        boolean[][][] inside = new boolean[gx][gy][gz];
        BoundingBox bbox = hull.boundingBox;
        Vector3 bmin = new Vector3(), bmax = new Vector3();
        bbox.getMin(bmin);
        bbox.getMax(bmax);

        float centerX = (bmin.x + bmax.x) / 2f;
        float centerY = (bmin.y + bmax.y) / 2f;
        float halfW   = (bmax.x - bmin.x) / 2f;
        float halfH   = (bmax.y - bmin.y) / 2f;
        float depth   = bmax.z - bmin.z;

        for (int x = 0; x < gx; x++) {
            for (int y = 0; y < gy; y++) {
                for (int z = 0; z < gz; z++) {
                    float wx = min.x + (x + 0.5f) * CELL_SIZE;
                    float wy = min.y + (y + 0.5f) * CELL_SIZE;
                    float wz = min.z + (z + 0.5f) * CELL_SIZE;

                    // Taper profile: narrow at nose, full at mid-body, slightly narrower at tail
                    float zFrac = (depth > 0f) ? (wz - bmin.z) / depth : 0.5f;
                    float taper;
                    if (zFrac < 0.15f)       taper = zFrac / 0.15f * 0.3f;
                    else if (zFrac < 0.6f)   taper = 0.3f + (zFrac - 0.15f) / 0.45f * 0.7f;
                    else                     taper = 1f - (zFrac - 0.6f) / 0.4f * 0.4f;
                    taper = Math.max(0.05f, taper);

                    float localW = halfW * taper;
                    float localH = halfH * taper;

                    float dx = (localW > 0f) ? (wx - centerX) / localW : 0f;
                    float dy = (localH > 0f) ? (wy - centerY) / localH : 0f;

                    inside[x][y][z] = (dx * dx + dy * dy) < 1f;
                }
            }
        }
        return inside;
    }

    /** Erodes the voxel mask by one cell so hull-wall thickness is preserved. */
    private boolean[][][] shrinkMask(boolean[][][] inside, int gx, int gy, int gz) {
        boolean[][][] result = new boolean[gx][gy][gz];
        for (int x = 1; x < gx - 1; x++) {
            for (int y = 1; y < gy - 1; y++) {
                for (int z = 1; z < gz - 1; z++) {
                    result[x][y][z] = inside[x][y][z]
                        && inside[x-1][y][z] && inside[x+1][y][z]
                        && inside[x][y-1][z] && inside[x][y+1][z]
                        && inside[x][y][z-1] && inside[x][y][z+1];
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Room manifest
    // -------------------------------------------------------------------------

    private List<RoomType> getRoomManifest(ShipSizeClass sizeClass, Random rng) {
        List<RoomType> manifest = new ArrayList<>();
        manifest.add(RoomType.COCKPIT);
        if (sizeClass == ShipSizeClass.MEDIUM || sizeClass == ShipSizeClass.LARGE) {
            manifest.add(RoomType.ENGINE_ROOM);
        }
        if (sizeClass == ShipSizeClass.LARGE) manifest.add(RoomType.CARGO_BAY);
        if ((sizeClass == ShipSizeClass.MEDIUM || sizeClass == ShipSizeClass.LARGE) && rng.nextBoolean())
            manifest.add(RoomType.CARGO_BAY);
        if ((sizeClass == ShipSizeClass.MEDIUM || sizeClass == ShipSizeClass.LARGE) && rng.nextBoolean())
            manifest.add(RoomType.CREW_QUARTERS);
        if (sizeClass == ShipSizeClass.LARGE && rng.nextBoolean()) manifest.add(RoomType.MEDBAY);
        if (sizeClass == ShipSizeClass.LARGE && rng.nextBoolean()) manifest.add(RoomType.ARMORY);
        return manifest;
    }

    // -------------------------------------------------------------------------
    // Room packing
    // -------------------------------------------------------------------------

    private List<RoomPlacement> packRooms(List<RoomType> manifest, boolean[][][] packable,
                                          int gx, int gy, int gz, Random rng) {
        List<RoomPlacement> placements = new ArrayList<>();
        boolean[][][] occupied = new boolean[gx][gy][gz];

        // Sort: cockpit first (nose/high-Z), engine room last (tail/low-Z), large rooms before small
        manifest.sort((a, b) -> {
            if (a == RoomType.COCKPIT)    return -1;
            if (b == RoomType.COCKPIT)    return  1;
            if (a == RoomType.ENGINE_ROOM) return  1;
            if (b == RoomType.ENGINE_ROOM) return -1;
            return Integer.compare(b.minSizeX * b.minSizeZ, a.minSizeX * a.minSizeZ);
        });

        for (RoomType roomType : manifest) {
            int sx = roomType.minSizeX + rng.nextInt(Math.max(1, roomType.maxSizeX - roomType.minSizeX + 1));
            int sy = roomType.minSizeY + rng.nextInt(Math.max(1, roomType.maxSizeY - roomType.minSizeY + 1));
            int sz = roomType.minSizeZ + rng.nextInt(Math.max(1, roomType.maxSizeZ - roomType.minSizeZ + 1));
            sx = Math.min(sx, gx);
            sy = Math.min(sy, gy);
            sz = Math.min(sz, gz);

            // Try progressively smaller sizes down to 1x1x1 to guarantee placement on small hulls
            RoomPlacement placement = null;
            for (int trySx = sx; trySx >= 1 && placement == null; trySx--) {
                for (int trySy = sy; trySy >= 1 && placement == null; trySy--) {
                    for (int trySz = sz; trySz >= 1 && placement == null; trySz--) {
                        placement = findPlacement(roomType, trySx, trySy, trySz, packable, occupied, gx, gy, gz);
                    }
                }
            }
            if (placement != null) {
                placements.add(placement);
                markOccupied(occupied, placement);
            }
        }
        return placements;
    }

    /**
     * Searches for a valid placement position.
     * ENGINE_ROOM scans from tail (low Z) forward; all others scan from nose (high Z) backward.
     * Falls back to scanning the full grid if the preferred direction yields nothing.
     */
    private RoomPlacement findPlacement(RoomType type, int sx, int sy, int sz,
                                        boolean[][][] packable, boolean[][][] occupied,
                                        int gx, int gy, int gz) {
        if (sx > gx || sy > gy || sz > gz) return null;

        int zStart, zEnd, zStep;
        if (type == RoomType.ENGINE_ROOM) {
            zStart = 0;        zEnd = gz - sz; zStep =  1;
        } else {
            zStart = gz - sz;  zEnd = 0;       zStep = -1;
        }

        for (int z = zStart; zStep > 0 ? z <= zEnd : z >= zEnd; z += zStep) {
            for (int x = 0; x <= gx - sx; x++) {
                for (int y = 0; y <= gy - sy; y++) {
                    if (canPlace(x, y, z, sx, sy, sz, packable, occupied)) {
                        return new RoomPlacement(type, x, y, z, sx, sy, sz);
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlace(int px, int py, int pz, int sx, int sy, int sz,
                             boolean[][][] packable, boolean[][][] occupied) {
        for (int x = px; x < px + sx; x++)
            for (int y = py; y < py + sy; y++)
                for (int z = pz; z < pz + sz; z++)
                    if (!packable[x][y][z] || occupied[x][y][z]) return false;
        return true;
    }

    private void markOccupied(boolean[][][] occupied, RoomPlacement room) {
        for (int x = room.gridX; x < room.gridX + room.sizeX; x++)
            for (int y = room.gridY; y < room.gridY + room.sizeY; y++)
                for (int z = room.gridZ; z < room.gridZ + room.sizeZ; z++)
                    occupied[x][y][z] = true;
    }

    // -------------------------------------------------------------------------
    // Corridors
    // -------------------------------------------------------------------------

    /** Connects consecutive rooms with L-shaped corridor paths through packable, unoccupied cells. */
    private void connectRoomsWithCorridors(List<RoomPlacement> rooms, boolean[][][] corridors,
                                           boolean[][][] packable, int gx, int gy, int gz) {
        if (rooms.size() < 2) return;
        for (int i = 0; i < rooms.size() - 1; i++) {
            RoomPlacement from = rooms.get(i);
            RoomPlacement to   = rooms.get(i + 1);

            // Walk from centre of `from` to centre of `to` via an L-shaped path
            int fx = from.gridX + from.sizeX / 2;
            int fy = from.gridY;
            int fz = from.gridZ + from.sizeZ / 2;
            int tx = to.gridX + to.sizeX / 2;
            int tz = to.gridZ + to.sizeZ / 2;

            // Leg 1: along Z
            int step = (tz > fz) ? 1 : -1;
            for (int z = fz + step; z != tz + step; z += step) {
                if (z >= 0 && z < gz && fx >= 0 && fx < gx && fy >= 0 && fy < gy)
                    if (packable[fx][fy][z] && !isInAnyRoom(fx, fy, z, rooms))
                        corridors[fx][fy][z] = true;
            }

            // Leg 2: along X
            step = (tx > fx) ? 1 : -1;
            for (int x = fx + step; x != tx + step; x += step) {
                if (x >= 0 && x < gx && fy >= 0 && fy < gy && tz >= 0 && tz < gz)
                    if (packable[x][fy][tz] && !isInAnyRoom(x, fy, tz, rooms))
                        corridors[x][fy][tz] = true;
            }
        }
    }

    private boolean isInAnyRoom(int x, int y, int z, List<RoomPlacement> rooms) {
        for (RoomPlacement room : rooms)
            if (room.contains(x, y, z)) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Special position lookup
    // -------------------------------------------------------------------------

    private Vector3 findAirlockPosition(List<RoomPlacement> rooms, Vector3 gridOrigin) {
        if (rooms.isEmpty()) return new Vector3(0, 0, 0);
        float avgX = 0, avgZ = 0, minY = Float.MAX_VALUE;
        for (RoomPlacement room : rooms) {
            avgX += (room.gridX + room.sizeX / 2f);
            avgZ += (room.gridZ + room.sizeZ / 2f);
            minY = Math.min(minY, room.gridY);
        }
        avgX /= rooms.size();
        avgZ /= rooms.size();
        return new Vector3(
            gridOrigin.x + avgX * CELL_SIZE,
            gridOrigin.y + minY * CELL_SIZE + 0.5f,
            gridOrigin.z + avgZ * CELL_SIZE);
    }

    private Vector3 findPilotSeatPosition(List<RoomPlacement> rooms, Vector3 gridOrigin) {
        for (RoomPlacement room : rooms) {
            if (room.type == RoomType.COCKPIT) {
                return new Vector3(
                    gridOrigin.x + (room.gridX + room.sizeX / 2f) * CELL_SIZE,
                    gridOrigin.y + room.gridY * CELL_SIZE + 0.5f,
                    gridOrigin.z + (room.gridZ + room.sizeZ / 2f) * CELL_SIZE);
            }
        }
        return findAirlockPosition(rooms, gridOrigin);
    }

    // -------------------------------------------------------------------------
    // Mesh generation
    // -------------------------------------------------------------------------

    /**
     * Emits a single floor quad (4 vertices, 6 indices) for the given room.
     *
     * @return updated vertex offset after the new vertices are appended
     */
    private int generateRoomFloor(RoomPlacement room, Vector3 gridOrigin,
                                  List<float[]> vertsList, List<short[]> indsList,
                                  int vertOffset) {
        float ox = gridOrigin.x + room.gridX * CELL_SIZE;
        float oy = gridOrigin.y + room.gridY * CELL_SIZE;
        float oz = gridOrigin.z + room.gridZ * CELL_SIZE;
        float w  = room.sizeX * CELL_SIZE;
        float d  = room.sizeZ * CELL_SIZE;
        float r  = room.type.floorColor.r;
        float g  = room.type.floorColor.g;
        float b  = room.type.floorColor.b;

        float[] verts = {
            ox,     oy, oz,     0, 1, 0, r, g, b, 1,
            ox + w, oy, oz,     0, 1, 0, r, g, b, 1,
            ox + w, oy, oz + d, 0, 1, 0, r, g, b, 1,
            ox,     oy, oz + d, 0, 1, 0, r, g, b, 1,
        };
        short base = (short) vertOffset;
        short[] inds = {
            base, (short)(base+1), (short)(base+2),
            base, (short)(base+2), (short)(base+3)
        };
        vertsList.add(verts);
        indsList.add(inds);
        return vertOffset + 4;
    }

    /**
     * Emits ceiling + four wall quads for the given room.
     *
     * @return updated vertex offset after all new vertices are appended
     */
    private int generateRoomWalls(RoomPlacement room, Vector3 gridOrigin,
                                  List<float[]> vertsList, List<short[]> indsList,
                                  int vertOffset) {
        float ox = gridOrigin.x + room.gridX * CELL_SIZE;
        float oy = gridOrigin.y + room.gridY * CELL_SIZE;
        float oz = gridOrigin.z + room.gridZ * CELL_SIZE;
        float w  = room.sizeX * CELL_SIZE;
        float h  = room.sizeY * CELL_SIZE;
        float d  = room.sizeZ * CELL_SIZE;
        float r  = room.type.accentColor.r;
        float g  = room.type.accentColor.g;
        float b  = room.type.accentColor.b;

        // Ceiling quad (normal pointing down into the room = -Y)
        float[] ceilVerts = {
            ox,     oy+h, oz+d, 0,-1,0, r*0.7f, g*0.7f, b*0.7f, 1,
            ox+w,   oy+h, oz+d, 0,-1,0, r*0.7f, g*0.7f, b*0.7f, 1,
            ox+w,   oy+h, oz,   0,-1,0, r*0.7f, g*0.7f, b*0.7f, 1,
            ox,     oy+h, oz,   0,-1,0, r*0.7f, g*0.7f, b*0.7f, 1,
        };
        short cBase = (short) vertOffset;
        short[] cInds = {
            cBase, (short)(cBase+1), (short)(cBase+2),
            cBase, (short)(cBase+2), (short)(cBase+3)
        };
        vertsList.add(ceilVerts);
        indsList.add(cInds);
        vertOffset += 4;

        // Four wall quads: -X, +X, -Z, +Z
        // Each row: [v0x,v0y,v0z, v1x,v1y,v1z, v2x,v2y,v2z, v3x,v3y,v3z, nx,ny,nz]
        float[][] walls = {
            { ox,   oy,   oz,   ox,   oy+h, oz,   ox,   oy+h, oz+d, ox,   oy,   oz+d, -1, 0, 0 },
            { ox+w, oy,   oz+d, ox+w, oy+h, oz+d, ox+w, oy+h, oz,   ox+w, oy,   oz,    1, 0, 0 },
            { ox+w, oy,   oz,   ox+w, oy+h, oz,   ox,   oy+h, oz,   ox,   oy,   oz,    0, 0,-1 },
            { ox,   oy,   oz+d, ox,   oy+h, oz+d, ox+w, oy+h, oz+d, ox+w, oy,   oz+d,  0, 0, 1 },
        };

        for (float[] wall : walls) {
            float nx = wall[12], ny = wall[13], nz = wall[14];
            float[] verts = {
                wall[0], wall[1], wall[2],  nx, ny, nz, r, g, b, 1,
                wall[3], wall[4], wall[5],  nx, ny, nz, r, g, b, 1,
                wall[6], wall[7], wall[8],  nx, ny, nz, r, g, b, 1,
                wall[9], wall[10],wall[11], nx, ny, nz, r, g, b, 1,
            };
            short wBase = (short) vertOffset;
            short[] wInds = {
                wBase, (short)(wBase+1), (short)(wBase+2),
                wBase, (short)(wBase+2), (short)(wBase+3)
            };
            vertsList.add(verts);
            indsList.add(wInds);
            vertOffset += 4;
        }
        return vertOffset;
    }

    // -------------------------------------------------------------------------
    // Array merge helpers
    // -------------------------------------------------------------------------

    private float[] mergeFloats(List<float[]> arrays) {
        int total = 0;
        for (float[] a : arrays) total += a.length;
        float[] result = new float[total];
        int pos = 0;
        for (float[] a : arrays) { System.arraycopy(a, 0, result, pos, a.length); pos += a.length; }
        return result;
    }

    private short[] mergeShorts(List<short[]> arrays) {
        int total = 0;
        for (short[] a : arrays) total += a.length;
        short[] result = new short[total];
        int pos = 0;
        for (short[] a : arrays) { System.arraycopy(a, 0, result, pos, a.length); pos += a.length; }
        return result;
    }
}
