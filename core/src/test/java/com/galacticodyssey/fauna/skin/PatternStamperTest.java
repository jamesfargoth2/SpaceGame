package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import com.galacticodyssey.fauna.geometry.ProceduralMeshData;
import com.galacticodyssey.fauna.geometry.ProceduralPartMesher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternStamperTest {

    @Test
    void outputHasFourFloatsPerVertex() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f;
        spec.radius = 0.5f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 42L);
        assertEquals(data.positionCount() * 4, colors.length, "4 color floats per vertex");
    }

    @Test
    void dorsalVerticesHaveLowR_ventralHaveHighR() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f;
        spec.radius = 0.5f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 42L);

        boolean foundDorsal = false, foundVentral = false;
        for (int v = 0; v < data.positionCount(); v++) {
            float ny = data.normals[v * 3 + 1];
            float r = colors[v * 4];
            if (ny > 0.5f) { assertTrue(r < 0.5f, "dorsal vertex should have low R"); foundDorsal = true; }
            if (ny < -0.5f) { assertTrue(r > 0.5f, "ventral vertex should have high R"); foundVentral = true; }
        }
        assertTrue(foundDorsal, "should have dorsal vertices");
        assertTrue(foundVentral, "should have ventral vertices");
    }

    @Test
    void limbAxisGradientRunsZeroToOne() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f;
        spec.radius = 0.5f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 42L);

        float minG = Float.MAX_VALUE, maxG = -Float.MAX_VALUE;
        for (int v = 0; v < data.positionCount(); v++) {
            float g = colors[v * 4 + 1];
            minG = Math.min(minG, g);
            maxG = Math.max(maxG, g);
            assertTrue(g >= 0f && g <= 1f, "G must be in [0,1]");
        }
        assertTrue(minG < 0.1f, "proximal vertices should have G near 0");
        assertTrue(maxG > 0.9f, "distal vertices should have G near 1");
    }

    @Test
    void allChannelsInZeroOneRange() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CONE;
        spec.length = 1f;
        spec.radius = 0.3f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 99L);

        for (int i = 0; i < colors.length; i++) {
            assertTrue(colors[i] >= 0f && colors[i] <= 1f,
                "color channel " + (i % 4) + " at vertex " + (i / 4) + " = " + colors[i] + " out of [0,1]");
        }
    }
}
