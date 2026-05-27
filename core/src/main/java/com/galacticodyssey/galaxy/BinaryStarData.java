package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;

public final class BinaryStarData {
    public final SpectralClass spectralClass;
    public final float mass;
    public final float luminosity;
    public final float temperature;
    public final float separationAU;
    public final Color color;

    public BinaryStarData(SpectralClass spectralClass, float mass, float luminosity,
                          float temperature, float separationAU, Color color) {
        this.spectralClass = spectralClass;
        this.mass = mass;
        this.luminosity = luminosity;
        this.temperature = temperature;
        this.separationAU = separationAU;
        this.color = color;
    }
}
