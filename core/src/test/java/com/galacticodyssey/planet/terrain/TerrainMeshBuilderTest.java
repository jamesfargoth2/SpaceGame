package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMeshBuilderTest {
    private static final int GRID_SIZE = 33;
    private static final int VERTEX_STRIDE = 10; // pos(3) + normal(3) + color(4)

    @Test
    void vertexCountIs33x33() {
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        assertEquals(GRID_SIZE * GRID_SIZE * VERTEX_STRIDE, data.vertices.length,
            "Expected " + (GRID_SIZE * GRID_SIZE) + " vertices × " + VERTEX_STRIDE + " floats");
    }

    @Test
    void indexCountProducesValidTriangles() {
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        assertEquals(0, data.indices.length % 3, "Index count must be divisible by 3");
        int expectedQuads = (GRID_SIZE - 1) * (GRID_SIZE - 1);
        assertEquals(expectedQuads * 6, data.indices.length,
            "Expected " + expectedQuads + " quads × 6 indices");
    }

    @Test
    void normalsPointOutward() {
        // chunkCenterKm and radiusKm must match buildTestMesh() exactly.
        final double centerX = 0.0, centerY = 0.0, centerZ = 1.0; // PlanetCoordsKM(0,0,1)
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        for (int i = 0; i < data.vertices.length; i += VERTEX_STRIDE) {
            // Vertex is chunk-local metres. Reconstruct planet-space position (km).
            double vx = data.vertices[i];
            double vy = data.vertices[i + 1];
            double vz = data.vertices[i + 2];
            float nx = data.vertices[i + 3];
            float ny = data.vertices[i + 4];
            float nz = data.vertices[i + 5];
            // planetPos (km) = chunkCenterKm + localMetres * 0.001
            double px = centerX + vx * 0.001;
            double py = centerY + vy * 0.001;
            double pz = centerZ + vz * 0.001;
            // Normalize to get the outward radial direction.
            double len = Math.sqrt(px * px + py * py + pz * pz);
            double rx = px / len;
            double ry = py / len;
            double rz = pz / len;
            double dot = rx * nx + ry * ny + rz * nz;
            assertTrue(dot > 0.0, "Normal at vertex " + (i / VERTEX_STRIDE) +
                " points inward (dot=" + dot + ")");
        }
    }

    @Test
    void noNaNInVertices() {
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        for (int i = 0; i < data.vertices.length; i++) {
            assertFalse(Float.isNaN(data.vertices[i]),
                "NaN found at vertex float index " + i);
        }
    }

    private TerrainMeshBuilder.MeshData buildTestMesh() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        // Use a stub chunk centre at the POS_Z face midpoint (0,0,1) * 1km.
        PlanetCoordsKM stubCenter = new PlanetCoordsKM(0.0, 0.0, 1.0);
        return TerrainMeshBuilder.build(CubeFace.POS_Z, 0f, 0f, 1f, 1f,
            noise, biomeMap, 1.0, stubCenter, 2, null);
    }
}
