package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.collision.BoundingBox;

/** GL-free mesh data for one flora model: separate trunk and foliage geometry (stride 6). */
public final class FloraMeshData {
    public final float[] trunkVertices;
    public final short[] trunkIndices;
    public final float[] foliageVertices;
    public final short[] foliageIndices;
    public final BoundingBox bounds;

    public FloraMeshData(float[] trunkVertices, short[] trunkIndices,
                         float[] foliageVertices, short[] foliageIndices, BoundingBox bounds) {
        this.trunkVertices = trunkVertices;
        this.trunkIndices = trunkIndices;
        this.foliageVertices = foliageVertices;
        this.foliageIndices = foliageIndices;
        this.bounds = bounds;
    }

    public boolean hasFoliage() { return foliageVertices.length > 0; }
}
