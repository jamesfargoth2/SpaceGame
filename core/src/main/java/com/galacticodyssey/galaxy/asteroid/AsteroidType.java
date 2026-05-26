package com.galacticodyssey.galaxy.asteroid;

/**
 * Classification of asteroid composition and surface characteristics.
 */
public enum AsteroidType {
    C_TYPE(0.05f, 0.03f, 0.01f),
    S_TYPE(0.15f, 0.10f, 0.03f),
    M_TYPE(0.08f, 0.15f, 0.05f),
    CONTACT_BINARY(0.50f, 0.10f, 0.02f);

    public final float lobeFactor;
    public final float bumpFactor;
    public final float roughnessFactor;

    AsteroidType(float lobeFactor, float bumpFactor, float roughnessFactor) {
        this.lobeFactor = lobeFactor;
        this.bumpFactor = bumpFactor;
        this.roughnessFactor = roughnessFactor;
    }
}
