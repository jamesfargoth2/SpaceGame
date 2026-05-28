package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;

public final class RoomDesign {
    public RoomType type;
    public int gridX, gridY, gridZ;
    public int sizeX, sizeY, sizeZ;

    public RoomDesign() {}

    public RoomDesign(RoomType type, int gridX, int gridY, int gridZ, int sizeX, int sizeY, int sizeZ) {
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridZ = gridZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    public boolean overlaps(RoomDesign other) {
        return gridX < other.gridX + other.sizeX && gridX + sizeX > other.gridX
            && gridY < other.gridY + other.sizeY && gridY + sizeY > other.gridY
            && gridZ < other.gridZ + other.sizeZ && gridZ + sizeZ > other.gridZ;
    }

    public boolean containsCell(int x, int y, int z) {
        return x >= gridX && x < gridX + sizeX
            && y >= gridY && y < gridY + sizeY
            && z >= gridZ && z < gridZ + sizeZ;
    }

    public RoomDesign copy() {
        return new RoomDesign(type, gridX, gridY, gridZ, sizeX, sizeY, sizeZ);
    }
}
