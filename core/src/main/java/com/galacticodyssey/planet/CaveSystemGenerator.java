package com.galacticodyssey.planet;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class CaveSystemGenerator {

    private static final CaveRoomType[] ROOM_TYPES = CaveRoomType.values();

    public CaveSystem generate(long seed, CaveBiome biome, int complexity) {
        long caveSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.CAVE_DOMAIN), 0);
        Random rng = new Random(caveSeed);

        int roomCount = complexity * 3 + RngUtil.range(rng, 2, 5);
        int depthLayers = Math.max(2, complexity);
        float layerSpacing = RngUtil.range(rng, 15f, 30f);

        List<CaveRoom> rooms = new ArrayList<>();
        for (int layer = 0; layer < depthLayers; layer++) {
            int roomsInLayer = roomCount / depthLayers + (layer == 0 ? roomCount % depthLayers : 0);
            float baseY = -layer * layerSpacing;

            for (int i = 0; i < roomsInLayer; i++) {
                float angle = rng.nextFloat() * MathUtils.PI2;
                float dist = RngUtil.range(rng, 10f, 50f) * (1 + layer * 0.5f);
                float x = dist * MathUtils.cos(angle);
                float z = dist * MathUtils.sin(angle);
                float y = baseY + RngUtil.range(rng, -5f, 5f);

                float radius = RngUtil.range(rng, 5f, 20f);
                float height = RngUtil.range(rng, 3f, radius * 1.5f);
                CaveRoomType type = pickRoomType(biome, rng);

                rooms.add(new CaveRoom(rooms.size(), new Vector3(x, y, z), radius, height, type, layer));
            }
        }

        List<CaveTunnel> tunnels = connectRooms(rooms, rng, complexity);

        List<Vector3> entrances = new ArrayList<>();
        for (CaveRoom room : rooms) {
            if (room.depthLayer == 0 && rng.nextFloat() < 0.3f) {
                entrances.add(new Vector3(room.position.x, 0f, room.position.z));
            }
        }
        if (entrances.isEmpty() && !rooms.isEmpty()) {
            CaveRoom first = rooms.get(0);
            entrances.add(new Vector3(first.position.x, 0f, first.position.z));
        }

        return new CaveSystem(caveSeed, biome, rooms, tunnels, depthLayers, entrances);
    }

    private List<CaveTunnel> connectRooms(List<CaveRoom> rooms, Random rng, int complexity) {
        List<CaveTunnel> tunnels = new ArrayList<>();
        if (rooms.size() < 2) return tunnels;

        boolean[] connected = new boolean[rooms.size()];
        connected[0] = true;
        int connectedCount = 1;

        while (connectedCount < rooms.size()) {
            float bestDist = Float.MAX_VALUE;
            int bestFrom = -1, bestTo = -1;

            for (int i = 0; i < rooms.size(); i++) {
                if (!connected[i]) continue;
                for (int j = 0; j < rooms.size(); j++) {
                    if (connected[j]) continue;
                    float dist = rooms.get(i).position.dst(rooms.get(j).position);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestFrom = i;
                        bestTo = j;
                    }
                }
            }

            if (bestFrom >= 0) {
                connected[bestTo] = true;
                connectedCount++;
                float width = RngUtil.range(rng, 2f, 6f);
                float dy = rooms.get(bestTo).position.y - rooms.get(bestFrom).position.y;
                float slope = dy / Math.max(1f, bestDist);
                boolean hazard = rng.nextFloat() < 0.2f;
                tunnels.add(new CaveTunnel(bestFrom, bestTo, width, slope, hazard));
            }
        }

        int extras = complexity;
        for (int i = 0; i < extras; i++) {
            int a = rng.nextInt(rooms.size());
            int b = rng.nextInt(rooms.size());
            if (a != b) {
                float dist = rooms.get(a).position.dst(rooms.get(b).position);
                float width = RngUtil.range(rng, 1.5f, 4f);
                float dy = rooms.get(b).position.y - rooms.get(a).position.y;
                float slope = dy / Math.max(1f, dist);
                tunnels.add(new CaveTunnel(a, b, width, slope, rng.nextFloat() < 0.3f));
            }
        }

        return tunnels;
    }

    private CaveRoomType pickRoomType(CaveBiome biome, Random rng) {
        return switch (biome) {
            case CRYSTAL -> rng.nextFloat() < 0.4f ? CaveRoomType.CRYSTAL_CAVE : ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
            case VOLCANIC -> rng.nextFloat() < 0.4f ? CaveRoomType.LAVA_TUBE : ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
            case ICE -> rng.nextFloat() < 0.3f ? CaveRoomType.GALLERY : ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
            default -> ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
        };
    }
}
