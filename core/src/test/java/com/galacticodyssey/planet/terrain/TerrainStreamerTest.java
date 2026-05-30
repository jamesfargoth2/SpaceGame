package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainStreamerTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private TerrainNoiseStack noise() { return new TerrainNoiseStack(99L); }
    private BiomeMap biome() {
        return new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
    }

    @Test
    void drainProducesSameMeshAsSynchronousBuild() throws Exception {
        TerrainNoiseStack noise = noise();
        BiomeMap biome = biome();
        double radiusKm = 6371.0;
        TerrainChunk chunk = new TerrainChunk(CubeFace.POS_Z, 4, 0.1f, 0.1f, 0.2f, 0.2f, radiusKm);

        TerrainMeshBuilder.MeshData ref = TerrainMeshBuilder.build(
            chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
            noise, biome, radiusKm, chunk.centerPlanetKm, chunk.depth, null);

        TerrainStreamer streamer = new TerrainStreamer(noise, biome, radiusKm, 1);
        List<GeneratedChunkData> drained = new ArrayList<>();
        chunk.state = TerrainChunk.State.GENERATING;
        chunk.wantsCollision = false;
        streamer.submit(chunk);

        for (int i = 0; i < 200 && drained.isEmpty(); i++) {
            streamer.drain(8, drained::add);
            Thread.sleep(5);
        }
        assertEquals(1, drained.size(), "expected one drained result");
        GeneratedChunkData g = drained.get(0);
        assertArrayEquals(ref.vertices, g.vertices, 0f);
        assertArrayEquals(ref.indices, g.indices);
        assertNull(g.collisionShape, "render-only chunk must not build a shape");
        streamer.dispose();
    }

    @Test
    void collisionChunkBuildsShapeOffThread() throws Exception {
        TerrainStreamer streamer = new TerrainStreamer(noise(), biome(), 6371.0, 1);
        TerrainChunk chunk = new TerrainChunk(CubeFace.POS_Z, 8, 0.1f, 0.1f, 0.11f, 0.11f, 6371.0);
        chunk.state = TerrainChunk.State.GENERATING;
        chunk.wantsCollision = true;
        streamer.submit(chunk);

        List<GeneratedChunkData> drained = new ArrayList<>();
        for (int i = 0; i < 200 && drained.isEmpty(); i++) {
            streamer.drain(8, drained::add);
            Thread.sleep(5);
        }
        assertEquals(1, drained.size());
        assertNotNull(drained.get(0).collisionShape, "collision chunk must carry a prebuilt shape");
        assertNotNull(drained.get(0).triangleMesh);
        assertEquals(chunk.generationToken(), drained.get(0).token);
        drained.get(0).disposeNative();
        streamer.dispose();
    }

    @Test
    void disposeShutsDownPool() {
        TerrainStreamer streamer = new TerrainStreamer(noise(), biome(), 6371.0, 2);
        streamer.dispose();
        assertTrue(streamer.isShutdown());
    }
}
