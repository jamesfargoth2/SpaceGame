package com.galacticodyssey.ship;

public class RoomPlacement {
    public final RoomType type;
    public final int gridX, gridY, gridZ;
    public final int sizeX, sizeY, sizeZ;

    public RoomPlacement(RoomType type, int gridX, int gridY, int gridZ,
                         int sizeX, int sizeY, int sizeZ) {
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridZ = gridZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= gridX && x < gridX + sizeX
            && y >= gridY && y < gridY + sizeY
            && z >= gridZ && z < gridZ + sizeZ;
    }
}
