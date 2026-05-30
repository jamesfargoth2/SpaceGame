package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.planet.BiomeMap;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Builds terrain chunk geometry (and, when requested, collision shapes) on a background thread
 * pool, returning completed {@link GeneratedChunkData} for the main thread to drain. All work
 * here is pure CPU: no GL calls, no dynamics-world mutation. The main thread owns GL upload and
 * {@code dynamicsWorld.addRigidBody} (see {@link TerrainQuadtree}).
 */
public final class TerrainStreamer implements Disposable {
    private final TerrainNoiseStack noise;
    private final BiomeMap biomeMap;
    private final double radiusKm;
    private final ExecutorService pool;
    private final ConcurrentLinkedQueue<GeneratedChunkData> results = new ConcurrentLinkedQueue<>();
    private volatile boolean shutdown;

    public TerrainStreamer(TerrainNoiseStack noise, BiomeMap biomeMap, double radiusKm, int threads) {
        this.noise = noise;
        this.biomeMap = biomeMap;
        this.radiusKm = radiusKm;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "terrain-streamer");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(Math.max(1, threads), tf);
    }

    public static int defaultThreadCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }

    public void submit(TerrainChunk chunk) {
        // submit() is expected to be called only from the main thread (same thread as dispose()).
        if (shutdown) return;
        final int token = chunk.generationToken();
        final boolean wantsCollision = chunk.wantsCollision;
        pool.execute(() -> {
            if (shutdown || Thread.currentThread().isInterrupted()) return;
            try {
                TerrainMeshBuilder.MeshData data = TerrainMeshBuilder.build(
                    chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
                    noise, biomeMap, radiusKm, chunk.centerPlanetKm, chunk.depth, null);

                btBvhTriangleMeshShape shape = null;
                btTriangleMesh triMesh = null;
                if (wantsCollision) {
                    triMesh = buildTriangleMesh(data);
                    shape = new btBvhTriangleMeshShape(triMesh, true);
                }
                GeneratedChunkData result = new GeneratedChunkData(
                    chunk, token, data.vertices, data.indices, shape, triMesh);
                results.add(result);
                if (shutdown && results.remove(result)) {
                    // dispose() drained before we enqueued — reclaim native objects so they don't leak.
                    result.disposeNative();
                }
            } catch (Throwable t) {
                results.add(new GeneratedChunkData(chunk, token, null, null, null, null));
            }
        });
    }

    private btTriangleMesh buildTriangleMesh(TerrainMeshBuilder.MeshData data) {
        btTriangleMesh triMesh = new btTriangleMesh();
        Vector3 v0 = new Vector3(), v1 = new Vector3(), v2 = new Vector3();
        int stride = TerrainMeshBuilder.VERTEX_STRIDE;
        for (int i = 0; i < data.indices.length; i += 3) {
            int i0 = (data.indices[i] & 0xFFFF) * stride;
            int i1 = (data.indices[i + 1] & 0xFFFF) * stride;
            int i2 = (data.indices[i + 2] & 0xFFFF) * stride;
            v0.set(data.vertices[i0], data.vertices[i0 + 1], data.vertices[i0 + 2]);
            v1.set(data.vertices[i1], data.vertices[i1 + 1], data.vertices[i1 + 2]);
            v2.set(data.vertices[i2], data.vertices[i2 + 1], data.vertices[i2 + 2]);
            triMesh.addTriangle(v0, v1, v2);
        }
        return triMesh;
    }

    public void drain(int max, Consumer<GeneratedChunkData> consumer) {
        for (int i = 0; i < max; i++) {
            GeneratedChunkData g = results.poll();
            if (g == null) return;
            consumer.accept(g);
        }
    }

    public boolean isShutdown() { return shutdown; }

    @Override
    public void dispose() {
        shutdown = true;
        pool.shutdownNow();
        GeneratedChunkData g;
        while ((g = results.poll()) != null) g.disposeNative();
    }
}
