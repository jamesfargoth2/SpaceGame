package com.galacticodyssey.planet;

import java.util.List;

public final class DungeonLayout {
    public final long seed;
    public final DungeonTheme theme;
    public final List<DungeonRoom> rooms;
    public final List<DungeonConnection> connections;
    public final List<EncounterSlot> encounterSlots;
    public final int totalArea;

    public DungeonLayout(long seed, DungeonTheme theme, List<DungeonRoom> rooms,
                         List<DungeonConnection> connections, List<EncounterSlot> encounterSlots) {
        this.seed = seed;
        this.theme = theme;
        this.rooms = List.copyOf(rooms);
        this.connections = List.copyOf(connections);
        this.encounterSlots = List.copyOf(encounterSlots);
        this.totalArea = rooms.stream().mapToInt(DungeonRoom::area).sum();
    }
}
