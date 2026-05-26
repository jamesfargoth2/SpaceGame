package com.galacticodyssey.ship;

import java.util.Random;

public class ShipBlueprint {

    public final long seed;
    public final ShipSizeClass sizeClass;
    public final float spineLength;
    public final int crossSectionCount;
    public final float maxWidth;
    public final float maxHeight;
    public final int wingPairs;
    public final int enginePodCount;

    public ShipBlueprint(long seed, ShipSizeClass sizeClass) {
        this.seed = seed;
        this.sizeClass = sizeClass;

        Random rng = new Random(seed);
        this.spineLength = lerp(sizeClass.minSpineLength, sizeClass.maxSpineLength, rng.nextFloat());
        this.crossSectionCount = sizeClass.minCrossSections +
            rng.nextInt(sizeClass.maxCrossSections - sizeClass.minCrossSections + 1);
        this.maxWidth = lerp(sizeClass.minWidth, sizeClass.maxWidth, rng.nextFloat());
        this.maxHeight = lerp(sizeClass.minHeight, sizeClass.maxHeight, rng.nextFloat());
        this.wingPairs = sizeClass.minWingPairs +
            rng.nextInt(sizeClass.maxWingPairs - sizeClass.minWingPairs + 1);
        this.enginePodCount = sizeClass.minEnginePods +
            rng.nextInt(sizeClass.maxEnginePods - sizeClass.minEnginePods + 1);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
