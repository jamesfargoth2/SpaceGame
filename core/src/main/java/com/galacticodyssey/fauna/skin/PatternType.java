package com.galacticodyssey.fauna.skin;

public enum PatternType {
    SOLID(0), STRIPES(1), SPOTS(2), ROSETTES(3), MOTTLED(4), BIOLUMINESCENT(5);

    public final int shaderId;

    PatternType(int shaderId) { this.shaderId = shaderId; }
}
