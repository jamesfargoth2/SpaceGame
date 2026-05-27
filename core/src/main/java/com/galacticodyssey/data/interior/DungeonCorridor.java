package com.galacticodyssey.data.interior;

/**
 * An L-shaped corridor connecting two rooms in a generated dungeon.
 */
public final class DungeonCorridor {

    public final String fromRoomId;
    public final String toRoomId;
    public final float midX;
    public final float midY;
    public final boolean isLocked;
    public final String keyId;

    public DungeonCorridor(String fromRoomId, String toRoomId,
                           float midX, float midY,
                           boolean isLocked, String keyId) {
        this.fromRoomId = fromRoomId;
        this.toRoomId = toRoomId;
        this.midX = midX;
        this.midY = midY;
        this.isLocked = isLocked;
        this.keyId = keyId;
    }
}
