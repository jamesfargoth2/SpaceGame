package com.galacticodyssey.data.interior;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

/**
 * Procedurally generates dungeon interior layouts using BSP partitioning,
 * Kruskal's MST for corridors, and a lock-and-key gating system.
 * Pure data generator -- no libGDX dependencies.
 */
public final class DungeonGenerator {

    public DungeonGenerator() {}

    /**
     * Generate a dungeon interior from the given configuration.
     */
    public GeneratedDungeon generate(DungeonConfig cfg) {
        long domainSeed = SeedDeriver.domain(cfg.seed, SeedDeriver.INTERIOR_DOMAIN);
        Random rng = new Random(domainSeed);

        // Step 1: BSP split.
        BSPNode root = new BSPNode(0f, 0f, cfg.boundsWidth, cfg.boundsHeight);
        splitBSP(root, 0, cfg.maxBSPDepth, cfg.minRoomSize, rng);

        // Step 2: Place rooms in leaf cells.
        List<BSPNode> leaves = new ArrayList<>();
        collectLeaves(root, leaves);

        List<RoomBuilder> rooms = new ArrayList<>();
        for (BSPNode leaf : leaves) {
            float roomW = RngUtil.range(rng, leaf.w * 0.55f, leaf.w * 0.85f);
            float roomH = RngUtil.range(rng, leaf.h * 0.55f, leaf.h * 0.85f);
            float roomX = RngUtil.range(rng, leaf.x, leaf.x + leaf.w - roomW);
            float roomY = RngUtil.range(rng, leaf.y, leaf.y + leaf.h - roomH);
            String id = "room_" + rooms.size();
            rooms.add(new RoomBuilder(id, roomX, roomY, roomW, roomH, leaf));
        }

        if (rooms.isEmpty()) {
            return new GeneratedDungeon(Collections.emptyList(), Collections.emptyList());
        }

        // Step 3: MST corridors via Kruskal's algorithm.
        List<Edge> allEdges = buildCompleteGraph(rooms);
        allEdges.sort(Comparator.comparingDouble(e -> e.distance));

        UnionFind uf = new UnionFind(rooms.size());
        List<Edge> mstEdges = new ArrayList<>();
        List<Edge> nonMstEdges = new ArrayList<>();

        for (Edge edge : allEdges) {
            if (uf.union(edge.fromIndex, edge.toIndex)) {
                mstEdges.add(edge);
            } else {
                nonMstEdges.add(edge);
            }
        }

        // Record connections from MST.
        Set<String> corridorKeys = new HashSet<>();
        List<CorridorBuilder> corridors = new ArrayList<>();

        for (Edge edge : mstEdges) {
            addCorridor(rooms, corridors, corridorKeys, edge);
        }

        // Step 4: Extra corridors for loops.
        int extraCount = Math.round(mstEdges.size() * cfg.extraCorridorFraction);
        Collections.shuffle(nonMstEdges, rng);
        for (int i = 0; i < Math.min(extraCount, nonMstEdges.size()); i++) {
            addCorridor(rooms, corridors, corridorKeys, nonMstEdges.get(i));
        }

        // Step 5: Assign room purposes based on distance from center.
        float centerX = cfg.boundsWidth * 0.5f;
        float centerY = cfg.boundsHeight * 0.5f;

        // Sort rooms by distance from center for purpose assignment.
        List<RoomBuilder> byDistance = new ArrayList<>(rooms);
        byDistance.sort(Comparator.comparingDouble(r -> {
            float dx = r.centerX() - centerX;
            float dy = r.centerY() - centerY;
            return dx * dx + dy * dy;
        }));

        // Map sorted indices back to the original room builders.
        byDistance.get(0).purpose = RoomPurpose.SPAWN;
        if (byDistance.size() > 1) {
            byDistance.get(byDistance.size() - 1).purpose = RoomPurpose.OBJECTIVE;
        }

        if (byDistance.size() > 2) {
            int bossIndex = (int) (byDistance.size() * 0.75f);
            bossIndex = Math.min(bossIndex, byDistance.size() - 2); // don't overwrite OBJECTIVE
            byDistance.get(bossIndex).purpose = RoomPurpose.BOSS_ROOM;
        }

        if (byDistance.size() > 3) {
            int bridgeIndex = (int) (byDistance.size() * 0.25f);
            bridgeIndex = Math.max(bridgeIndex, 1); // don't overwrite SPAWN
            if (byDistance.get(bridgeIndex).purpose == null) {
                byDistance.get(bridgeIndex).purpose = RoomPurpose.BRIDGE;
            }
        }

        // Fill remaining purposes.
        for (RoomBuilder room : byDistance) {
            if (room.purpose == null) {
                room.purpose = rollRoomPurpose(rng);
            }
        }

        // Step 6: Lock-and-key gating.
        // Compute BFS distances from spawn for lock placement.
        RoomBuilder spawnRoom = byDistance.get(0);
        Map<String, Integer> bfsDepth = bfsFromRoom(spawnRoom.id, rooms, corridors);

        // Find chokepoint corridors: those connecting rooms at different BFS depths.
        List<Integer> chokepointIndices = new ArrayList<>();
        for (int i = 0; i < corridors.size(); i++) {
            CorridorBuilder c = corridors.get(i);
            Integer depthFrom = bfsDepth.get(c.fromRoomId);
            Integer depthTo = bfsDepth.get(c.toRoomId);
            if (depthFrom != null && depthTo != null && Math.abs(depthFrom - depthTo) >= 1) {
                // Corridor at a depth boundary (connecting different BFS layers).
                chokepointIndices.add(i);
            }
        }

        // Sort chokepoints by the deeper end's depth so we lock further corridors first.
        chokepointIndices.sort((a, b) -> {
            CorridorBuilder ca = corridors.get(a);
            CorridorBuilder cb = corridors.get(b);
            int depthA = Math.max(bfsDepth.getOrDefault(ca.fromRoomId, 0),
                                  bfsDepth.getOrDefault(ca.toRoomId, 0));
            int depthB = Math.max(bfsDepth.getOrDefault(cb.fromRoomId, 0),
                                  bfsDepth.getOrDefault(cb.toRoomId, 0));
            return Integer.compare(depthB, depthA);
        });

        Collections.shuffle(chokepointIndices, rng);

        int gatesPlaced = 0;
        for (int ci : chokepointIndices) {
            if (gatesPlaced >= cfg.gateCount) break;
            CorridorBuilder c = corridors.get(ci);

            // Determine spawn-side room (lower BFS depth).
            Integer depthFrom = bfsDepth.getOrDefault(c.fromRoomId, 0);
            Integer depthTo = bfsDepth.getOrDefault(c.toRoomId, 0);
            String spawnSideRoomId = depthFrom <= depthTo ? c.fromRoomId : c.toRoomId;

            // Find a room reachable from spawn without crossing this corridor to place the key.
            String keyRoomId = findKeyRoom(spawnSideRoomId, spawnRoom.id, rooms, corridors, ci, rng);
            if (keyRoomId != null) {
                String keyId = "key_" + gatesPlaced;
                c.isLocked = true;
                c.keyId = keyId;
                gatesPlaced++;
            }
        }

        // Build final output.
        List<DungeonRoom> finalRooms = new ArrayList<>();
        for (RoomBuilder rb : rooms) {
            finalRooms.add(new DungeonRoom(rb.id, rb.x, rb.y, rb.width, rb.height,
                    rb.purpose, new ArrayList<>(rb.connectedRoomIds)));
        }

        List<DungeonCorridor> finalCorridors = new ArrayList<>();
        for (CorridorBuilder cb : corridors) {
            finalCorridors.add(new DungeonCorridor(cb.fromRoomId, cb.toRoomId,
                    cb.midX, cb.midY, cb.isLocked, cb.keyId));
        }

        return new GeneratedDungeon(finalRooms, finalCorridors);
    }

    // -- BSP Splitting ------------------------------------------------------------

    private void splitBSP(BSPNode node, int depth, int maxDepth, float minRoomSize, Random rng) {
        if (depth >= maxDepth) return;
        if (node.w < minRoomSize * 2 && node.h < minRoomSize * 2) return;

        boolean splitHorizontal;
        if (node.w < minRoomSize * 2) {
            splitHorizontal = true; // can only split along Y
        } else if (node.h < minRoomSize * 2) {
            splitHorizontal = false; // can only split along X
        } else if (Math.abs(node.w - node.h) < 1e-3f) {
            splitHorizontal = rng.nextBoolean(); // square: random
        } else {
            splitHorizontal = node.h > node.w; // split along longer axis
        }

        float splitFraction = RngUtil.range(rng, 0.35f, 0.65f);

        if (splitHorizontal) {
            float splitY = node.y + node.h * splitFraction;
            node.left = new BSPNode(node.x, node.y, node.w, splitY - node.y);
            node.right = new BSPNode(node.x, splitY, node.w, node.y + node.h - splitY);
        } else {
            float splitX = node.x + node.w * splitFraction;
            node.left = new BSPNode(node.x, node.y, splitX - node.x, node.h);
            node.right = new BSPNode(splitX, node.y, node.x + node.w - splitX, node.h);
        }

        splitBSP(node.left, depth + 1, maxDepth, minRoomSize, rng);
        splitBSP(node.right, depth + 1, maxDepth, minRoomSize, rng);
    }

    private void collectLeaves(BSPNode node, List<BSPNode> leaves) {
        if (node.left == null && node.right == null) {
            leaves.add(node);
        } else {
            if (node.left != null) collectLeaves(node.left, leaves);
            if (node.right != null) collectLeaves(node.right, leaves);
        }
    }

    // -- Graph construction -------------------------------------------------------

    private List<Edge> buildCompleteGraph(List<RoomBuilder> rooms) {
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                float dx = rooms.get(i).centerX() - rooms.get(j).centerX();
                float dy = rooms.get(i).centerY() - rooms.get(j).centerY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                edges.add(new Edge(i, j, dist));
            }
        }
        return edges;
    }

    private void addCorridor(List<RoomBuilder> rooms, List<CorridorBuilder> corridors,
                             Set<String> corridorKeys, Edge edge) {
        RoomBuilder from = rooms.get(edge.fromIndex);
        RoomBuilder to = rooms.get(edge.toIndex);
        String key = corridorKey(from.id, to.id);
        if (corridorKeys.add(key)) {
            // L-shaped: horizontal first, then vertical. Mid-point at (toX, fromY).
            float midX = to.centerX();
            float midY = from.centerY();
            corridors.add(new CorridorBuilder(from.id, to.id, midX, midY));
            from.connectedRoomIds.add(to.id);
            to.connectedRoomIds.add(from.id);
        }
    }

    private String corridorKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    // -- Room purpose assignment --------------------------------------------------

    private static final RoomPurpose[] FILL_PURPOSES = {
        RoomPurpose.STORAGE,     // 20%
        RoomPurpose.BARRACKS,    // 15%
        RoomPurpose.ENGINEERING, // 15%
        RoomPurpose.MEDBAY,      // 10%
        RoomPurpose.AIRLOCK,     // 10%
        RoomPurpose.GENERIC      // 30%
    };
    private static final float[] FILL_WEIGHTS = { 0.20f, 0.35f, 0.50f, 0.60f, 0.70f, 1.00f };

    private RoomPurpose rollRoomPurpose(Random rng) {
        float roll = rng.nextFloat();
        for (int i = 0; i < FILL_WEIGHTS.length; i++) {
            if (roll < FILL_WEIGHTS[i]) {
                return FILL_PURPOSES[i];
            }
        }
        return RoomPurpose.GENERIC;
    }

    // -- BFS and lock-and-key helpers ---------------------------------------------

    private Map<String, Integer> bfsFromRoom(String startId, List<RoomBuilder> rooms,
                                              List<CorridorBuilder> corridors) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (RoomBuilder r : rooms) {
            adjacency.put(r.id, new HashSet<>(r.connectedRoomIds));
        }

        Map<String, Integer> depth = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        depth.put(startId, 0);
        queue.add(startId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depth.get(current);
            Set<String> neighbors = adjacency.getOrDefault(current, Collections.emptySet());
            for (String neighbor : neighbors) {
                if (!depth.containsKey(neighbor)) {
                    depth.put(neighbor, currentDepth + 1);
                    queue.add(neighbor);
                }
            }
        }
        return depth;
    }

    /**
     * Find a room on the spawn side of a locked corridor to place a key.
     * Uses BFS from spawn, excluding the locked corridor.
     */
    private String findKeyRoom(String spawnSideRoomId, String spawnRoomId,
                                List<RoomBuilder> rooms, List<CorridorBuilder> corridors,
                                int excludeCorridorIndex, Random rng) {
        // Build adjacency excluding the locked corridor.
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (RoomBuilder r : rooms) {
            adjacency.put(r.id, new HashSet<>());
        }
        for (int i = 0; i < corridors.size(); i++) {
            if (i == excludeCorridorIndex) continue;
            CorridorBuilder c = corridors.get(i);
            adjacency.get(c.fromRoomId).add(c.toRoomId);
            adjacency.get(c.toRoomId).add(c.fromRoomId);
        }

        // BFS from spawn to find reachable rooms.
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        reachable.add(spawnRoomId);
        queue.add(spawnRoomId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbor : adjacency.getOrDefault(current, Collections.emptySet())) {
                if (reachable.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        // Pick a reachable room (preferring non-spawn rooms).
        List<String> candidates = new ArrayList<>();
        for (String roomId : reachable) {
            if (!roomId.equals(spawnRoomId)) {
                candidates.add(roomId);
            }
        }
        if (candidates.isEmpty()) {
            // Only spawn is reachable; place key there.
            return spawnRoomId;
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }

    // -- Internal data structures -------------------------------------------------

    private static final class BSPNode {
        final float x, y, w, h;
        BSPNode left;
        BSPNode right;

        BSPNode(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class RoomBuilder {
        final String id;
        final float x, y, width, height;
        final BSPNode leaf;
        final List<String> connectedRoomIds = new ArrayList<>();
        RoomPurpose purpose;

        RoomBuilder(String id, float x, float y, float width, float height, BSPNode leaf) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.leaf = leaf;
        }

        float centerX() { return x + width * 0.5f; }
        float centerY() { return y + height * 0.5f; }
    }

    private static final class CorridorBuilder {
        final String fromRoomId;
        final String toRoomId;
        final float midX;
        final float midY;
        boolean isLocked;
        String keyId;

        CorridorBuilder(String fromRoomId, String toRoomId, float midX, float midY) {
            this.fromRoomId = fromRoomId;
            this.toRoomId = toRoomId;
            this.midX = midX;
            this.midY = midY;
        }
    }

    private static final class Edge {
        final int fromIndex;
        final int toIndex;
        final float distance;

        Edge(int fromIndex, int toIndex, float distance) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.distance = distance;
        }
    }

    private static final class UnionFind {
        private final int[] parent;
        private final int[] rank;

        UnionFind(int size) {
            parent = new int[size];
            rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]); // path compression
            }
            return parent[x];
        }

        boolean union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) return false;
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
            return true;
        }
    }
}
