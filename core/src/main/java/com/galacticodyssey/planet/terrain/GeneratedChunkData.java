package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;

/**
 * Immutable result of an off-thread chunk build, handed back to the main thread for GL upload
 * and rigid-body registration. CPU-only fields; no GL/Bullet-world references.
 */
public final class GeneratedChunkData {
    public final TerrainChunk chunk;
    public final int token;
    public final float[] vertices;
    public final short[] indices;
    public final btBvhTriangleMeshShape collisionShape; // null for render-only chunks
    public final btTriangleMesh triangleMesh;           // null for render-only chunks

    public GeneratedChunkData(TerrainChunk chunk, int token, float[] vertices, short[] indices,
                              btBvhTriangleMeshShape collisionShape, btTriangleMesh triangleMesh) {
        this.chunk = chunk;
        this.token = token;
        this.vertices = vertices;
        this.indices = indices;
        this.collisionShape = collisionShape;
        this.triangleMesh = triangleMesh;
    }

    /** Dispose the prebuilt native collision objects (used when a result is stale or rejected). */
    public void disposeNative() {
        if (collisionShape != null) collisionShape.dispose();
        if (triangleMesh != null) triangleMesh.dispose();
    }
}
