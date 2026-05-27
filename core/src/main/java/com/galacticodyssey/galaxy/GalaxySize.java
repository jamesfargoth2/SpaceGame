package com.galacticodyssey.galaxy;

public enum GalaxySize {
    SMALL(500),
    MEDIUM(2000),
    LARGE(10000);

    public final int starCount;

    GalaxySize(int starCount) {
        this.starCount = starCount;
    }
}
