package com.galacticodyssey.shipbuilder;

public class DesignMetadata {
    public long createdAt;
    public long lastModified;
    public int buildCost;
    public float totalMass;
    public float totalPowerDraw;

    public DesignMetadata() {
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }
}
