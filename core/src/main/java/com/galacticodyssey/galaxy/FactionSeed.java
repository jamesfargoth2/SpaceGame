package com.galacticodyssey.galaxy;

public final class FactionSeed {
    public final String factionId;
    public final double startX;
    public final double startY;
    public final float strength;
    public final float aggressiveness;

    public FactionSeed(String factionId, double startX, double startY, float strength, float aggressiveness) {
        this.factionId = factionId;
        this.startX = startX;
        this.startY = startY;
        this.strength = strength;
        this.aggressiveness = aggressiveness;
    }
}
