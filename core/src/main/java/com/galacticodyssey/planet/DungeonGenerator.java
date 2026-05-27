package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class DungeonGenerator {

    public DungeonLayout generate(long seed, DungeonTheme theme, int roomCount) {
        long dungeonSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.DUNGEON_DOMAIN), 0);
        Random rng = new Random(dungeonSeed);

        int minSize = minRoomSizeForTheme(theme);
        int maxSize = maxRoomSizeForTheme(theme);
        int gridSize = (int) Math.ceil(Math.sqrt(roomCount)) * maxSize * 2;

        List<DungeonRoom> rooms = new ArrayList<>();
        List<int[]> candidates = new ArrayList<>();
        candidates.add(new int[]{0, 0, gridSize, gridSize});

        while (rooms.size() < roomCount && !candidates.isEmpty()) {
            int idx = rng.nextInt(candidates.size());
            int[] region = candidates.remove(idx);
            int rx = region[0], ry = region[1], rw = region[2], rh = region[3];

            if (rw < minSize * 2 || rh < minSize * 2) continue;

            if (rw > maxSize * 2 || rh > maxSize * 2) {
                if (rng.nextBoolean() && rw > minSize * 2) {
                    int split = rx + RngUtil.range(rng, minSize, rw - minSize);
                    candidates.add(new int[]{rx, ry, split - rx, rh});
                    candidates.add(new int[]{split, ry, rx + rw - split, rh});
                } else if (rh > minSize * 2) {
                    int split = ry + RngUtil.range(rng, minSize, rh - minSize);
                    candidates.add(new int[]{rx, ry, rw, split - ry});
                    candidates.add(new int[]{rx, split, rw, ry + rh - split});
                }
                continue;
            }

            int roomW = RngUtil.range(rng, minSize, Math.min(maxSize, rw - 2) + 1);
            int roomH = RngUtil.range(rng, minSize, Math.min(maxSize, rh - 2) + 1);
            int roomX = rx + RngUtil.range(rng, 1, Math.max(2, rw - roomW));
            int roomY = ry + RngUtil.range(rng, 1, Math.max(2, rh - roomH));

            DungeonRoomType type = assignRoomType(rooms.size(), roomCount, theme, rng);
            rooms.add(new DungeonRoom(rooms.size(), roomX, roomY, roomW, roomH, type));
        }

        List<DungeonConnection> connections = connectRooms(rooms, rng);
        List<EncounterSlot> encounters = placeEncounters(rooms, rng);

        return new DungeonLayout(dungeonSeed, theme, rooms, connections, encounters);
    }

    private List<DungeonConnection> connectRooms(List<DungeonRoom> rooms, Random rng) {
        List<DungeonConnection> connections = new ArrayList<>();
        for (int i = 0; i < rooms.size() - 1; i++) {
            DungeonRoom a = rooms.get(i);
            DungeonRoom b = rooms.get(i + 1);
            int doorX = (a.centerX() + b.centerX()) / 2;
            int doorY = (a.centerY() + b.centerY()) / 2;
            boolean locked = rng.nextFloat() < 0.15f;
            connections.add(new DungeonConnection(i, i + 1, doorX, doorY, locked));
        }
        for (int i = 0; i < rooms.size() / 4; i++) {
            int a = rng.nextInt(rooms.size());
            int b = rng.nextInt(rooms.size());
            if (a != b && Math.abs(a - b) > 1) {
                DungeonRoom ra = rooms.get(a);
                DungeonRoom rb = rooms.get(b);
                int doorX = (ra.centerX() + rb.centerX()) / 2;
                int doorY = (ra.centerY() + rb.centerY()) / 2;
                connections.add(new DungeonConnection(a, b, doorX, doorY, rng.nextFloat() < 0.3f));
            }
        }
        return connections;
    }

    private List<EncounterSlot> placeEncounters(List<DungeonRoom> rooms, Random rng) {
        List<EncounterSlot> slots = new ArrayList<>();
        for (DungeonRoom room : rooms) {
            if (room.type == DungeonRoomType.ENTRY || room.type == DungeonRoomType.CORRIDOR) continue;
            int difficulty = room.type == DungeonRoomType.BOSS_ARENA ? 10 :
                             room.type == DungeonRoomType.TRAP ? 7 : RngUtil.range(rng, 1, 8);
            slots.add(new EncounterSlot(room.id, room.centerX(), room.centerY(), difficulty));
        }
        return slots;
    }

    private DungeonRoomType assignRoomType(int index, int totalRooms, DungeonTheme theme, Random rng) {
        if (index == 0) return DungeonRoomType.ENTRY;
        if (index == totalRooms - 1) return DungeonRoomType.BOSS_ARENA;

        float roll = rng.nextFloat();
        float trapChance = trapChanceForTheme(theme);
        float lootChance = lootChanceForTheme(theme);
        float puzzleChance = puzzleChanceForTheme(theme);

        if (roll < trapChance) return DungeonRoomType.TRAP;
        if (roll < trapChance + lootChance) return DungeonRoomType.LOOT_ROOM;
        if (roll < trapChance + lootChance + puzzleChance) return DungeonRoomType.PUZZLE;
        if (rng.nextFloat() < 0.3f) return DungeonRoomType.GUARD_POST;
        return DungeonRoomType.STORAGE;
    }

    private int minRoomSizeForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 5; case MILITARY_BUNKER -> 4; case PIRATE_HIDEOUT -> 3; case ANCIENT_TEMPLE -> 6; };
    }

    private int maxRoomSizeForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 15; case MILITARY_BUNKER -> 10; case PIRATE_HIDEOUT -> 12; case ANCIENT_TEMPLE -> 20; };
    }

    private float trapChanceForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 0.25f; case MILITARY_BUNKER -> 0.15f; case PIRATE_HIDEOUT -> 0.3f; case ANCIENT_TEMPLE -> 0.2f; };
    }

    private float lootChanceForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 0.3f; case MILITARY_BUNKER -> 0.25f; case PIRATE_HIDEOUT -> 0.35f; case ANCIENT_TEMPLE -> 0.2f; };
    }

    private float puzzleChanceForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 0.2f; case MILITARY_BUNKER -> 0.1f; case PIRATE_HIDEOUT -> 0.05f; case ANCIENT_TEMPLE -> 0.35f; };
    }
}
