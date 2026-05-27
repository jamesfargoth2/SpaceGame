package com.galacticodyssey.planet;

public final class DungeonRoom {
    public final int id;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final DungeonRoomType type;

    public DungeonRoom(int id, int x, int y, int width, int height, DungeonRoomType type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
    }

    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }
    public int area() { return width * height; }
}
