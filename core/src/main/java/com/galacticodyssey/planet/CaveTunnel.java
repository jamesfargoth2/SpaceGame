package com.galacticodyssey.planet;

public final class CaveTunnel {
    public final int roomA;
    public final int roomB;
    public final float width;
    public final float slope;
    public final boolean hasHazard;

    public CaveTunnel(int roomA, int roomB, float width, float slope, boolean hasHazard) {
        this.roomA = roomA;
        this.roomB = roomB;
        this.width = width;
        this.slope = slope;
        this.hasHazard = hasHazard;
    }
}
