package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipHullGeneratorTest {

    @Test
    void generatesNonEmptyGeometry() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        HullGeometry geom = new ShipHullGenerator().generate(bp);
        assertTrue(geom.vertexCount()   > 0, "Should generate vertices");
        assertTrue(geom.triangleCount() > 0, "Should generate triangles");
    }

    @Test
    void boundingBoxIsReasonableForSmallShip() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        HullGeometry geom = new ShipHullGenerator().generate(bp);
        float width = geom.boundingBox.getWidth();
        float depth = geom.boundingBox.getDepth();
        assertTrue(width > 1f,  "Width should be > 1 m, was "  + width);
        assertTrue(width < 20f, "Width should be < 20 m for SMALL, was " + width);
        assertTrue(depth > 5f,  "Depth should be > 5 m, was "  + depth);
        assertTrue(depth < 20f, "Depth should be < 20 m for SMALL, was " + depth);
    }

    @Test
    void sameSeedProducesSameGeometry() {
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry a = gen.generate(new ShipBlueprint(42L, ShipSizeClass.MEDIUM));
        HullGeometry b = gen.generate(new ShipBlueprint(42L, ShipSizeClass.MEDIUM));
        assertEquals(a.vertexCount(),   b.vertexCount());
        assertEquals(a.triangleCount(), b.triangleCount());
        assertArrayEquals(a.vertices, b.vertices, 0.001f);
    }

    @Test
    void differentSizesProduceDifferentScales() {
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry small = gen.generate(new ShipBlueprint(42L, ShipSizeClass.SMALL));
        HullGeometry large = gen.generate(new ShipBlueprint(42L, ShipSizeClass.LARGE));
        assertTrue(large.boundingBox.getDepth() > small.boundingBox.getDepth(),
            "Large ship should be longer than small ship");
    }

    @Test
    void hasHardpoints() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.MEDIUM);
        HullGeometry geom = new ShipHullGenerator().generate(bp);
        assertTrue(geom.hardpoints.length > 0, "Should have at least one hardpoint");
    }

    @Test
    void vertexStrideMatchesFormat() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        HullGeometry geom = new ShipHullGenerator().generate(bp);
        assertEquals(ShipHullGenerator.VERTEX_STRIDE, geom.vertexStride,
            "Reported stride should match VERTEX_STRIDE constant");
        assertEquals(0, geom.vertices.length % geom.vertexStride,
            "Vertex array length must be divisible by stride");
    }

    @Test
    void indexCountIsConsistentWithTriangleCount() {
        ShipBlueprint bp = new ShipBlueprint(7L, ShipSizeClass.MEDIUM);
        HullGeometry geom = new ShipHullGenerator().generate(bp);
        assertEquals(geom.triangleCount() * 3, geom.indices.length,
            "Index array length must equal triangleCount * 3");
    }

    @Test
    void tailCapVertexIsEmissive() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        HullGeometry geom = new ShipHullGenerator().generate(bp);
        // The tail cap is the second-to-last vertex in the array (index totalVerts - 1)
        int tailVertexOffset = (geom.vertexCount() - 1) * ShipHullGenerator.VERTEX_STRIDE;
        float emissive = geom.vertices[tailVertexOffset + 10];
        assertEquals(1f, emissive, 0.001f, "Tail cap vertex should be fully emissive");
    }

    @Test
    void noseCapVertexIsNotEmissive() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        HullGeometry geom = new ShipHullGenerator().generate(bp);
        // The nose cap is the second-to-last vertex (index totalVerts - 2)
        int noseVertexOffset = (geom.vertexCount() - 2) * ShipHullGenerator.VERTEX_STRIDE;
        float emissive = geom.vertices[noseVertexOffset + 10];
        assertEquals(0f, emissive, 0.001f, "Nose cap vertex should not be emissive");
    }
}
