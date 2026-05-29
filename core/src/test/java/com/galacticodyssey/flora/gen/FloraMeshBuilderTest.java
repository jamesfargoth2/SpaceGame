package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import com.galacticodyssey.flora.data.FloraSpecies;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FloraMeshBuilderTest {
    private static BranchSkeleton skeleton(long seed) {
        Random rng = new Random(seed);
        Array<Vector3> pts = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 10f, 3f, 160, rng);
        SpaceColonization.GrowthParams p = new SpaceColonization.GrowthParams();
        return SpaceColonization.grow(pts, p, new Random(seed));
    }

    private static FloraSpecies species(FoliageStyle style) {
        FloraSpecies s = new FloraSpecies();
        s.trunkSides = 6; s.baseRadius = 0.3f; s.taper = 0.8f;
        s.foliageStyle = style; s.clumpsPerTip = 1;
        s.clumpRadiusMin = 1f; s.clumpRadiusMax = 1.5f;
        return s;
    }

    @Test
    void buildsNonEmptyTrunkGeometry() {
        FloraMeshData m = FloraMeshBuilder.build(skeleton(1), species(FoliageStyle.CLUMP), new Random(1));
        assertTrue(m.trunkVertices.length > 0);
        assertEquals(0, m.trunkVertices.length % 6, "stride is 6 floats");
        assertEquals(0, m.trunkIndices.length % 3, "triangles");
        assertFalse(m.bounds.getDimensions(new Vector3()).isZero(), "bounds must be non-degenerate");
    }

    @Test
    void normalsAreUnitLength() {
        FloraMeshData m = FloraMeshBuilder.build(skeleton(2), species(FoliageStyle.CLUMP), new Random(2));
        for (float[] verts : new float[][]{ m.trunkVertices, m.foliageVertices }) {
            for (int i = 0; i + 6 <= verts.length; i += 6) {
                float nx = verts[i + 3], ny = verts[i + 4], nz = verts[i + 5];
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                assertEquals(1f, len, 0.05f, "normal not unit length at vertex " + (i / 6));
            }
        }
    }

    @Test
    void foliageNoneProducesNoFoliage() {
        FloraMeshData m = FloraMeshBuilder.build(skeleton(3), species(FoliageStyle.NONE), new Random(3));
        assertEquals(0, m.foliageVertices.length);
        assertEquals(0, m.foliageIndices.length);
    }

    @Test
    void isDeterministic() {
        FloraMeshData a = FloraMeshBuilder.build(skeleton(4), species(FoliageStyle.CLUMP), new Random(4));
        FloraMeshData b = FloraMeshBuilder.build(skeleton(4), species(FoliageStyle.CLUMP), new Random(4));
        assertArrayEquals(a.trunkVertices, b.trunkVertices);
        assertArrayEquals(a.foliageVertices, b.foliageVertices);
    }
}
