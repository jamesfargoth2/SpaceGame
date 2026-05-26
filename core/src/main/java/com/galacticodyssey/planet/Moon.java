package com.galacticodyssey.planet;

public final class Moon {
    public final long seed;
    public final PlanetType type;
    public final float radius;
    public final float mass;
    public final float surfaceGravity;

    public Moon(long seed, PlanetType type, float radius, float mass) {
        this.seed = seed;
        this.type = type;
        this.radius = radius;
        this.mass = mass;
        this.surfaceGravity = mass / (radius * radius);
    }
}
