package com.galacticodyssey.ship;

public enum ShipSizeClass {
    SMALL(8f, 12f, 5, 7, 3f, 5f, 2f, 4f, 0, 1, 1, 2),
    MEDIUM(18f, 30f, 8, 12, 6f, 12f, 4f, 8f, 1, 2, 2, 4),
    LARGE(40f, 70f, 12, 18, 15f, 25f, 8f, 15f, 1, 3, 2, 6);

    public final float minSpineLength, maxSpineLength;
    public final int minCrossSections, maxCrossSections;
    public final float minWidth, maxWidth;
    public final float minHeight, maxHeight;
    public final int minWingPairs, maxWingPairs;
    public final int minEnginePods, maxEnginePods;

    ShipSizeClass(float minSpineLength, float maxSpineLength,
                  int minCrossSections, int maxCrossSections,
                  float minWidth, float maxWidth,
                  float minHeight, float maxHeight,
                  int minWingPairs, int maxWingPairs,
                  int minEnginePods, int maxEnginePods) {
        this.minSpineLength = minSpineLength;
        this.maxSpineLength = maxSpineLength;
        this.minCrossSections = minCrossSections;
        this.maxCrossSections = maxCrossSections;
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.minWingPairs = minWingPairs;
        this.maxWingPairs = maxWingPairs;
        this.minEnginePods = minEnginePods;
        this.maxEnginePods = maxEnginePods;
    }
}
