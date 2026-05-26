package com.galacticodyssey.galaxy.asteroid;

import java.util.List;

/**
 * Result of procedural asteroid generation containing mesh data and vein info.
 */
public final class GeneratedAsteroid {

    public final float[] vertices;
    public final int[] indices;
    public final List<VeinDeposit> veins;
    public final AsteroidConfig config;

    public GeneratedAsteroid(float[] vertices, int[] indices,
                             List<VeinDeposit> veins, AsteroidConfig config) {
        this.vertices = vertices;
        this.indices = indices;
        this.veins = veins;
        this.config = config;
    }
}
