package com.galacticodyssey.data.interior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single room in a generated dungeon interior (2D layout).
 */
public final class DungeonRoom {

    public final String id;
    public final float x;
    public final float y;
    public final float width;
    public final float height;
    public final RoomPurpose purpose;
    public final List<String> connectedRoomIds;

    public DungeonRoom(String id, float x, float y, float width, float height,
                       RoomPurpose purpose, List<String> connectedRoomIds) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.purpose = purpose;
        this.connectedRoomIds = Collections.unmodifiableList(new ArrayList<>(connectedRoomIds));
    }

    /** Center X of this room. */
    public float centerX() {
        return x + width * 0.5f;
    }

    /** Center Y of this room. */
    public float centerY() {
        return y + height * 0.5f;
    }
}
