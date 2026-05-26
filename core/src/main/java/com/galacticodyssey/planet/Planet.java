package com.galacticodyssey.planet;

import java.util.ArrayList;
import java.util.List;

public final class Planet {
    public final long seed;
    public final PlanetType type;
    public final float radius;
    public final float mass;
    public final float surfaceGravity;
    public final float dayLength;
    public final float axialTilt;
    public final boolean tidallyLocked;
    public Atmosphere atmosphere;
    public final List<Moon> moons;

    public Planet(long seed, PlanetType type, float radius, float mass,
                  float dayLength, float axialTilt, boolean tidallyLocked) {
        this.seed = seed;
        this.type = type;
        this.radius = radius;
        this.mass = mass;
        this.surfaceGravity = mass / (radius * radius);
        this.dayLength = dayLength;
        this.axialTilt = axialTilt;
        this.tidallyLocked = tidallyLocked;
        this.moons = new ArrayList<>();
    }
}
