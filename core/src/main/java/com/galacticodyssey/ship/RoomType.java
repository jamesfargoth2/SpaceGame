package com.galacticodyssey.ship;

import com.badlogic.gdx.graphics.Color;

public enum RoomType {
    COCKPIT(3, 3, 2, 4, 4, 3,
        new Color(0.25f, 0.25f, 0.25f, 1f), new Color(0.3f, 0.4f, 0.7f, 1f)),
    CORRIDOR(1, 1, 2, 1, 1, 3,
        new Color(0.4f, 0.4f, 0.4f, 1f), new Color(0.8f, 0.8f, 0.8f, 1f)),
    ENGINE_ROOM(3, 3, 2, 5, 5, 3,
        new Color(0.2f, 0.2f, 0.22f, 1f), new Color(0.8f, 0.4f, 0.2f, 1f)),
    CARGO_BAY(4, 3, 2, 6, 5, 3,
        new Color(0.35f, 0.28f, 0.18f, 1f), new Color(0.8f, 0.75f, 0.2f, 1f)),
    CREW_QUARTERS(3, 3, 2, 3, 3, 2,
        new Color(0.4f, 0.38f, 0.35f, 1f), new Color(0.85f, 0.85f, 0.8f, 1f)),
    MEDBAY(3, 2, 2, 3, 2, 2,
        new Color(0.8f, 0.8f, 0.8f, 1f), new Color(0.3f, 0.7f, 0.6f, 1f)),
    ARMORY(2, 2, 2, 2, 2, 2,
        new Color(0.25f, 0.25f, 0.25f, 1f), new Color(0.7f, 0.2f, 0.2f, 1f));

    public final int minSizeX, minSizeZ, minSizeY;
    public final int maxSizeX, maxSizeZ, maxSizeY;
    public final Color floorColor;
    public final Color accentColor;

    RoomType(int minSizeX, int minSizeZ, int minSizeY,
             int maxSizeX, int maxSizeZ, int maxSizeY,
             Color floorColor, Color accentColor) {
        this.minSizeX = minSizeX;
        this.minSizeZ = minSizeZ;
        this.minSizeY = minSizeY;
        this.maxSizeX = maxSizeX;
        this.maxSizeZ = maxSizeZ;
        this.maxSizeY = maxSizeY;
        this.floorColor = floorColor;
        this.accentColor = accentColor;
    }
}
