package com.galacticodyssey.planet;

@FunctionalInterface
public interface HeightSampler {
    float sample(float lonRad, float latRad);
}
