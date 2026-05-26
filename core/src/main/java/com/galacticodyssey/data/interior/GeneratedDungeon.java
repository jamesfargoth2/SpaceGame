package com.galacticodyssey.data.interior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output of dungeon interior generation: rooms and corridors.
 */
public final class GeneratedDungeon {

    public final List<DungeonRoom> rooms;
    public final List<DungeonCorridor> corridors;

    public GeneratedDungeon(List<DungeonRoom> rooms, List<DungeonCorridor> corridors) {
        this.rooms = Collections.unmodifiableList(new ArrayList<>(rooms));
        this.corridors = Collections.unmodifiableList(new ArrayList<>(corridors));
    }
}
