package com.galacticodyssey.planet.cave;

/** A mineral vein running along a portion of a tunnel. */
public final class CaveVein {
    /** Commodity identifier (e.g. "iron_ore"). */
    public final String mineral;
    /** Richness of the deposit, 0-1. */
    public final float richness;
    /** Parametric start position along the host tunnel, 0-1. */
    public final float tStart;
    /** Parametric end position along the host tunnel, 0-1. */
    public final float tEnd;
    /** Width of the vein in metres. */
    public final float widthM;

    public CaveVein(String mineral, float richness, float tStart, float tEnd, float widthM) {
        this.mineral = mineral;
        this.richness = richness;
        this.tStart = tStart;
        this.tEnd = tEnd;
        this.widthM = widthM;
    }
}
