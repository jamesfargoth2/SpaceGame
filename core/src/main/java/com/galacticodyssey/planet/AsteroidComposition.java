package com.galacticodyssey.planet;

public enum AsteroidComposition {
    CARBONACEOUS(0.6f, 0.3f),
    SILICATE(0.8f, 0.5f),
    METALLIC(0.4f, 0.2f),
    ICE(0.7f, 0.6f);

    public final float roughness;
    public final float craterDensity;

    AsteroidComposition(float roughness, float craterDensity) {
        this.roughness = roughness;
        this.craterDensity = craterDensity;
    }
}
