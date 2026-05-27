package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public final class StarSystem {
    public final long uniqueId;
    public final long seed;
    public final SpectralClass spectralClass;
    public final LuminosityClass luminosityClass;
    public final float temperature;
    public final float luminosity;
    public final float mass;
    public final float radius;
    public final float age;
    public final Color color;
    public final float habZoneInner;
    public final float habZoneOuter;
    public final float frostLine;
    public final float systemEdge;
    public final List<OrbitalSlot> orbits;
    public BinaryStarData companion;

    public StarSystem(long uniqueId, long seed, SpectralClass spectralClass,
                      LuminosityClass luminosityClass, float temperature,
                      float luminosity, float mass, float radius, float age,
                      Color color) {
        this.uniqueId = uniqueId;
        this.seed = seed;
        this.spectralClass = spectralClass;
        this.luminosityClass = luminosityClass;
        this.temperature = temperature;
        this.luminosity = luminosity;
        this.mass = mass;
        this.radius = radius;
        this.age = age;
        this.color = color;
        float sqrtLum = (float) Math.sqrt(luminosity);
        this.habZoneInner = sqrtLum * 0.75f;
        this.habZoneOuter = sqrtLum * 1.77f;
        this.frostLine = sqrtLum * 4.85f;
        this.systemEdge = sqrtLum * 40f;
        this.orbits = new ArrayList<>();
    }
}
