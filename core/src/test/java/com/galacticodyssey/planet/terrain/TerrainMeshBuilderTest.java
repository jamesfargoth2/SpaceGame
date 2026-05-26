package com.galacticodyssey.planet.terrain;

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
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        for (int i = 0; i < data.vertices.length; i += VERTEX_STRIDE) {
            float px = data.vertices[i];
            float py = data.vertices[i + 1];
            float pz = data.vertices[i + 2];
            float nx = data.vertices[i + 3];
            float ny = data.vertices[i + 4];
            float nz = data.vertices[i + 5];
            float dot = px * nx + py * ny + pz * nz;
            assertTrue(dot > 0f, "Normal at vertex " + (i / VERTEX_STRIDE) +
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
        return TerrainMeshBuilder.build(CubeFace.POS_Z, 0f, 0f, 1f, 1f,
            noise, biomeMap, 1.0f, 2, null);
    }
}
