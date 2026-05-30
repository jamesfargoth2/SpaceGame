package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMeshBuilderLocalTest {
    @Test
    void verticesAreChunkLocalMetresNotPlanetMagnitude() {
        double radiusKm = 6371.0;
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f, EnumSet.allOf(BiomeType.class));
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        float u0 = 0.49f, v0 = 0.49f, u1 = 0.51f, v1 = 0.51f;
        float mu = (u0 + u1) * 0.5f, mv = (v0 + v1) * 0.5f;
        com.badlogic.gdx.math.Vector3 c = CubeSphere.toSphere(CubeFace.POS_Y, mu, mv);
        PlanetCoordsKM chunkCenter = new PlanetCoordsKM(c.x * radiusKm, c.y * radiusKm, c.z * radiusKm);

        TerrainMeshBuilder.MeshData d = TerrainMeshBuilder.build(
            CubeFace.POS_Y, u0, v0, u1, v1, noise, biomeMap, radiusKm, chunkCenter, 3, null);

        int stride = TerrainMeshBuilder.VERTEX_STRIDE;
        for (int i = 0; i < d.vertices.length; i += stride) {
            float x = d.vertices[i], y = d.vertices[i + 1], z = d.vertices[i + 2];
            assertTrue(Math.abs(x) < 5.0e5, "x local metres too large: " + x);
            assertTrue(Math.abs(z) < 5.0e5, "z local metres too large: " + z);
            double px = chunkCenter.x() + x * 0.001;
            double py = chunkCenter.y() + y * 0.001;
            double pz = chunkCenter.z() + z * 0.001;
            double rKm = Math.sqrt(px*px + py*py + pz*pz);
            assertEquals(radiusKm, rKm, radiusKm * 0.02, "reconstructed radius off-surface");
        }
    }

    @Test
    void centreVertexIsNearLocalOrigin() {
        double radiusKm = 6371.0;
        BiomeMap biomeMap = new BiomeMap(7L, 0.2f, 0.8f, 0.5f, 288f, EnumSet.allOf(BiomeType.class));
        TerrainNoiseStack noise = new TerrainNoiseStack(7L);
        float u0 = 0.40f, v0 = 0.40f, u1 = 0.60f, v1 = 0.60f;
        com.badlogic.gdx.math.Vector3 c = CubeSphere.toSphere(CubeFace.POS_Y, 0.5f, 0.5f);
        PlanetCoordsKM chunkCenter = new PlanetCoordsKM(c.x * radiusKm, c.y * radiusKm, c.z * radiusKm);
        TerrainMeshBuilder.MeshData d = TerrainMeshBuilder.build(
            CubeFace.POS_Y, u0, v0, u1, v1, noise, biomeMap, radiusKm, chunkCenter, 1, null);
        int centerIdx = (16 * TerrainMeshBuilder.GRID_SIZE + 16) * TerrainMeshBuilder.VERTEX_STRIDE;
        float cx = d.vertices[centerIdx], cz = d.vertices[centerIdx + 2];
        assertTrue(Math.hypot(cx, cz) < 1000f, "centre vertex not near local origin: " + cx + "," + cz);
    }
}
