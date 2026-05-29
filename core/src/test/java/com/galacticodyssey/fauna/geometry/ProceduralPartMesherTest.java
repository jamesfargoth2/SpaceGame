package com.galacticodyssey.fauna.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralPartMesherTest {
    @Test
    void capsuleProducesNonEmptyMeshWithinExpectedBounds() {
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f; spec.radius = 0.5f; spec.taper = 1f;

        ProceduralMeshData m = new ProceduralPartMesher().build(spec);

        assertTrue(m.positionCount() >= 8, "expected a non-trivial mesh");
        assertEquals(0, m.indices.length % 3, "indices must be whole triangles");
        // length runs along +Z from 0..length; radius bounds X/Y
        assertTrue(m.maxZ() <= 2.001f && m.minZ() >= -0.001f, "Z within [0,length]");
        assertTrue(m.maxAbsXY() <= 0.501f, "XY within radius");
    }

    @Test
    void isDeterministic() {
        PartGeometrySpec spec = new PartGeometrySpec();
        ProceduralMeshData a = new ProceduralPartMesher().build(spec);
        ProceduralMeshData b = new ProceduralPartMesher().build(spec);
        assertArrayEquals(a.positions, b.positions, 1e-6f);
        assertArrayEquals(a.indices, b.indices);
    }

    @Test
    void normalsAreUnitLengthAndRadial() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f;
        spec.radius = 0.5f;
        ProceduralMeshData data = mesher.build(spec);

        assertNotNull(data.normals);
        assertEquals(data.positions.length, data.normals.length, "normals array must match positions");

        for (int i = 0; i < data.normals.length; i += 3) {
            float nx = data.normals[i], ny = data.normals[i+1], nz = data.normals[i+2];
            float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
            assertEquals(1f, len, 0.01f, "normal at vertex " + (i/3) + " should be unit length");
            assertEquals(0f, nz, 1e-5f, "radial normals should have zero Z component");
        }
    }
}
