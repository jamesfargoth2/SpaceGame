package com.galacticodyssey.planet.terrain;

public final class CraterProfile {
    public final long seed;
    public final float centerX;
    public final float centerZ;
    public final float radius;
    public final float depth;
    public final float rimHeight;
    public final float centralPeakHeight;
    public final float ejectaRadius;
    public final float age;

    public CraterProfile(long seed, float centerX, float centerZ, float radius, float depth,
                         float rimHeight, float centralPeakHeight, float ejectaRadius, float age) {
        this.seed = seed;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.depth = depth;
        this.rimHeight = rimHeight;
        this.centralPeakHeight = centralPeakHeight;
        this.ejectaRadius = ejectaRadius;
        this.age = age;
    }
}
