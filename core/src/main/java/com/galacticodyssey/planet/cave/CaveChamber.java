package com.galacticodyssey.planet.cave;

/** A single chamber (room) within a cave system. */
public final class CaveChamber {
    public final float cx, cy, cz;
    public final float radiusM;
    public final ChamberType type;
    public final boolean hasWater;
    public final boolean hasLava;

    public CaveChamber(float cx, float cy, float cz, float radiusM,
                       ChamberType type, boolean hasWater, boolean hasLava) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radiusM = radiusM;
        this.type = type;
        this.hasWater = hasWater;
        this.hasLava = hasLava;
    }
}
