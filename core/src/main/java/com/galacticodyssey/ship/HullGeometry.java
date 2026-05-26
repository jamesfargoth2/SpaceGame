package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

/**
 * Immutable result of a hull mesh generation pass.
 *
 * <p>Vertex layout (stride = {@link #vertexStride} = 11 floats per vertex):
 * <pre>
 *   [0..2]  position  (x, y, z)
 *   [3..5]  normal    (nx, ny, nz)
 *   [6..9]  color     (r, g, b, a)
 *   [10]    emissive  (0 = unlit, 1 = engine glow)
 * </pre>
 */
public class HullGeometry {

    public final float[] vertices;
    public final short[] indices;
    public final BoundingBox boundingBox;
    public final Vector3[] hardpoints;
    public final int vertexStride;

    public HullGeometry(float[] vertices, short[] indices, BoundingBox boundingBox,
                        Vector3[] hardpoints, int vertexStride) {
        this.vertices = vertices;
        this.indices = indices;
        this.boundingBox = boundingBox;
        this.hardpoints = hardpoints;
        this.vertexStride = vertexStride;
    }

    /** Returns total number of vertices in {@link #vertices}. */
    public int vertexCount() { return vertices.length / vertexStride; }

    /** Returns total number of triangles described by {@link #indices}. */
    public int triangleCount() { return indices.length / 3; }
}
