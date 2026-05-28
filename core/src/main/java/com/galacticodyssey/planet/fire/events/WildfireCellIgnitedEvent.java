package com.galacticodyssey.planet.fire.events;

public final class WildfireCellIgnitedEvent {
    public final int cellX;
    public final int cellY;
    public final float worldX;
    public final float worldZ;
    public WildfireCellIgnitedEvent(int cellX, int cellY, float worldX, float worldZ) {
        this.cellX = cellX; this.cellY = cellY; this.worldX = worldX; this.worldZ = worldZ;
    }
}
