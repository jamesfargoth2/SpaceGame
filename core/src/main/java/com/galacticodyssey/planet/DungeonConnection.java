package com.galacticodyssey.planet;

public final class DungeonConnection {
    public final int roomA;
    public final int roomB;
    public final int doorX;
    public final int doorY;
    public final boolean locked;

    public DungeonConnection(int roomA, int roomB, int doorX, int doorY, boolean locked) {
        this.roomA = roomA;
        this.roomB = roomB;
        this.doorX = doorX;
        this.doorY = doorY;
        this.locked = locked;
    }
}
