package com.galacticodyssey.galaxy;

import java.util.Random;

public final class RngUtil {

    private RngUtil() {}

    public static float range(Random rng, float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }

    public static int range(Random rng, int min, int maxExclusive) {
        return min + rng.nextInt(maxExclusive - min);
    }
}
