package com.galacticodyssey.planet;

public final class AsteroidShape {
    public final long seed;
    public final float[] vertices;
    public final short[] indices;
    public final float[] normals;
    public final float boundingRadius;
    public final AsteroidComposition composition;

    public AsteroidShape(long seed, float[] vertices, short[] indices, float[] normals,
                         float boundingRadius, AsteroidComposition composition) {
        this.seed = seed;
        this.vertices = vertices;
        this.indices = indices;
        this.normals = normals;
        this.boundingRadius = boundingRadius;
        this.composition = composition;
    }
}
