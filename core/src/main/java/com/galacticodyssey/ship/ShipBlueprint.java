package com.galacticodyssey.ship;

import com.galacticodyssey.ship.weapons.data.HardpointTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ShipBlueprint {

    public final long seed;
    public final ShipSizeClass sizeClass;
    public final List<HardpointTemplate> hardpoints = new ArrayList<>();
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

    public ShipBlueprint(ShipSizeClass sizeClass, float spineLength, int crossSectionCount,
                         float maxWidth, float maxHeight, int wingPairs, int enginePodCount) {
        this.seed = 0;
        this.sizeClass = sizeClass;
        this.spineLength = spineLength;
        this.crossSectionCount = crossSectionCount;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.wingPairs = wingPairs;
        this.enginePodCount = enginePodCount;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
