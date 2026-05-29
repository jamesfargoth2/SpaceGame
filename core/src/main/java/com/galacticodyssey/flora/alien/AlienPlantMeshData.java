package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.math.collision.BoundingBox;

/** GL-free mesh data for one alien plant. Stride 11: pos3 + normal3 + color4 + emissive1. */
public final class AlienPlantMeshData {
    public final float[] vertices;
    public final short[] indices;
    public final BoundingBox bounds;

    public AlienPlantMeshData(float[] vertices, short[] indices, BoundingBox bounds) {
        this.vertices = vertices;
        this.indices = indices;
        this.bounds = bounds;
    }
}
