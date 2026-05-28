package com.galacticodyssey.hacking;

public class GridTile {
    public TileType type;
    public int rotation;
    public boolean powered;
    public boolean isSource;
    public boolean isTarget;

    public GridTile(TileType type, int rotation) {
        this.type = type;
        this.rotation = rotation & 0x3;
        this.powered = false;
        this.isSource = false;
        this.isTarget = false;
    }

    public void rotateClockwise() {
        rotation = (rotation + 1) % 4;
    }
}
