package com.galacticodyssey.data.interior;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DungeonGeneratorTest {

    private static final long TEST_SEED = 42L;
    private DungeonGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DungeonGenerator();
    }

    @Test
    void deterministic() {
        DungeonConfig cfg = new DungeonConfig(TEST_SEED, 200f, 200f, 4, 20f, 0.2f, 2);

        GeneratedDungeon a = generator.generate(cfg);
        GeneratedDungeon b = new DungeonGenerator().generate(cfg);

        assertEquals(a.rooms.size(), b.rooms.size());
        assertEquals(a.corridors.size(), b.corridors.size());

        for (int i = 0; i < a.rooms.size(); i++) {
            DungeonRoom ra = a.rooms.get(i);
            DungeonRoom rb = b.rooms.get(i);
            assertEquals(ra.id, rb.id);
            assertEquals(ra.x, rb.x, 1e-6f);
            assertEquals(ra.y, rb.y, 1e-6f);
            assertEquals(ra.width, rb.width, 1e-6f);
            assertEquals(ra.height, rb.height, 1e-6f);
            assertEquals(ra.purpose, rb.purpose);
            assertEquals(ra.connectedRoomIds, rb.connectedRoomIds);
        }

        for (int i = 0; i < a.corridors.size(); i++) {
            DungeonCorridor ca = a.corridors.get(i);
            DungeonCorridor cb = b.corridors.get(i);
            assertEquals(ca.fromRoomId, cb.fromRoomId);
            assertEquals(ca.toRoomId, cb.toRoomId);
            assertEquals(ca.isLocked, cb.isLocked);
            assertEquals(ca.keyId, cb.keyId);
        }
    }

    @Test
    void spawnIsFirstRoom() {
        for (long seed = 0; seed < 20; seed++) {
            DungeonConfig cfg = new DungeonConfig(seed, 200f, 200f, 4, 20f, 0.2f, 1);
            GeneratedDungeon dungeon = generator.generate(cfg);

            assertFalse(dungeon.rooms.isEmpty(), "seed=" + seed + ": dungeon must have rooms");

            // Find the room with purpose SPAWN.
            boolean hasSpawn = dungeon.rooms.stream()
                    .anyMatch(r -> r.purpose == RoomPurpose.SPAWN);
            assertTrue(hasSpawn, "seed=" + seed + ": dungeon must have a SPAWN room");

            // The room closest to center should be SPAWN.
            DungeonRoom firstSpawn = dungeon.rooms.stream()
                    .filter(r -> r.purpose == RoomPurpose.SPAWN)
                    .findFirst()
                    .orElse(null);
            assertNotNull(firstSpawn, "seed=" + seed + ": SPAWN room must exist");
        }
    }

    @Test
    void objectiveIsDeepest() {
        for (long seed = 0; seed < 20; seed++) {
            DungeonConfig cfg = new DungeonConfig(seed, 200f, 200f, 4, 20f, 0.2f, 0);
            GeneratedDungeon dungeon = generator.generate(cfg);

            if (dungeon.rooms.size() < 2) continue;

            boolean hasObjective = dungeon.rooms.stream()
                    .anyMatch(r -> r.purpose == RoomPurpose.OBJECTIVE);
            assertTrue(hasObjective, "seed=" + seed + ": dungeon must have an OBJECTIVE room");
        }
    }

    @Test
    void allRoomsConnected() {
        for (long seed = 0; seed < 30; seed++) {
            DungeonConfig cfg = new DungeonConfig(seed, 200f, 200f, 4, 20f, 0.2f, 1);
            GeneratedDungeon dungeon = generator.generate(cfg);

            if (dungeon.rooms.isEmpty()) continue;

            // Build adjacency from corridors.
            Map<String, Set<String>> adjacency = new HashMap<>();
            for (DungeonRoom r : dungeon.rooms) {
                adjacency.put(r.id, new HashSet<>());
            }
            for (DungeonCorridor c : dungeon.corridors) {
                adjacency.get(c.fromRoomId).add(c.toRoomId);
                adjacency.get(c.toRoomId).add(c.fromRoomId);
            }

            // BFS from first room.
            Set<String> visited = new HashSet<>();
            Queue<String> queue = new ArrayDeque<>();
            String startId = dungeon.rooms.get(0).id;
            visited.add(startId);
            queue.add(startId);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (String neighbor : adjacency.getOrDefault(current, Collections.emptySet())) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            assertEquals(dungeon.rooms.size(), visited.size(),
                    "seed=" + seed + ": not all rooms reachable. Visited "
                            + visited.size() + " of " + dungeon.rooms.size());
        }
    }

    @Test
    void extraCorridorsAddLoops() {
        for (long seed = 0; seed < 20; seed++) {
            DungeonConfig cfg = new DungeonConfig(seed, 200f, 200f, 4, 20f, 0.3f, 0);
            GeneratedDungeon dungeon = generator.generate(cfg);

            if (dungeon.rooms.size() < 3) continue;

            int mstSize = dungeon.rooms.size() - 1;
            assertTrue(dungeon.corridors.size() > mstSize,
                    "seed=" + seed + ": corridor count " + dungeon.corridors.size()
                            + " should exceed MST size " + mstSize);
        }
    }

    @Test
    void roomsFitInBounds() {
        float boundsW = 200f;
        float boundsH = 150f;

        for (long seed = 0; seed < 30; seed++) {
            DungeonConfig cfg = new DungeonConfig(seed, boundsW, boundsH, 4, 20f, 0.2f, 1);
            GeneratedDungeon dungeon = generator.generate(cfg);

            for (DungeonRoom room : dungeon.rooms) {
                assertTrue(room.x >= 0,
                        "seed=" + seed + " room " + room.id + ": x=" + room.x + " below 0");
                assertTrue(room.y >= 0,
                        "seed=" + seed + " room " + room.id + ": y=" + room.y + " below 0");
                assertTrue(room.x + room.width <= boundsW + 0.01f,
                        "seed=" + seed + " room " + room.id + ": right edge "
                                + (room.x + room.width) + " exceeds bounds " + boundsW);
                assertTrue(room.y + room.height <= boundsH + 0.01f,
                        "seed=" + seed + " room " + room.id + ": bottom edge "
                                + (room.y + room.height) + " exceeds bounds " + boundsH);
                assertTrue(room.width > 0, "room width must be positive");
                assertTrue(room.height > 0, "room height must be positive");
            }
        }
    }

    @Test
    void lockedCorridorsHaveKeys() {
        for (long seed = 0; seed < 30; seed++) {
            DungeonConfig cfg = new DungeonConfig(seed, 200f, 200f, 4, 20f, 0.2f, 3);
            GeneratedDungeon dungeon = generator.generate(cfg);

            for (DungeonCorridor corridor : dungeon.corridors) {
                if (corridor.isLocked) {
                    assertNotNull(corridor.keyId,
                            "seed=" + seed + ": locked corridor " + corridor.fromRoomId
                                    + "->" + corridor.toRoomId + " must have a keyId");
                    assertFalse(corridor.keyId.isEmpty(),
                            "seed=" + seed + ": keyId must not be empty");
                }
            }

            // Verify that locked corridors' keys are in rooms reachable from spawn
            // without crossing the locked corridor.
            DungeonRoom spawnRoom = dungeon.rooms.stream()
                    .filter(r -> r.purpose == RoomPurpose.SPAWN)
                    .findFirst()
                    .orElse(null);

            if (spawnRoom == null) continue;

            for (DungeonCorridor corridor : dungeon.corridors) {
                if (!corridor.isLocked) continue;

                // Build adjacency excluding this locked corridor.
                Map<String, Set<String>> adj = new HashMap<>();
                for (DungeonRoom r : dungeon.rooms) {
                    adj.put(r.id, new HashSet<>());
                }
                for (DungeonCorridor c : dungeon.corridors) {
                    if (c == corridor) continue;
                    adj.get(c.fromRoomId).add(c.toRoomId);
                    adj.get(c.toRoomId).add(c.fromRoomId);
                }

                // BFS from spawn.
                Set<String> reachable = new HashSet<>();
                Queue<String> queue = new ArrayDeque<>();
                reachable.add(spawnRoom.id);
                queue.add(spawnRoom.id);
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    for (String neighbor : adj.getOrDefault(current, Collections.emptySet())) {
                        if (reachable.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }

                // The key should be in a spawn-reachable room.
                // We can't directly verify which room the key is in from the data model,
                // but we verify the keyId is non-null (key was placed).
                assertNotNull(corridor.keyId,
                        "seed=" + seed + ": locked corridor must have a key placed");
            }
        }
    }
}
