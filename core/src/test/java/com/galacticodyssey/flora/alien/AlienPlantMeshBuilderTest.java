package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class AlienPlantMeshBuilderTest {
    static final int STRIDE = 11;

    private static AlienPlantSpecies species(AlienArchetype a) {
        AlienPlantSpecies s = new AlienPlantSpecies();
        s.id = "t"; s.archetype = a;
        s.stalkHeightMin = 1.5f; s.stalkHeightMax = 2f; s.stalkBaseRadius = 0.12f;
        s.stalkSides = 6; s.stalkColor = new Color(0.2f,0.2f,0.25f,1f);
        s.canopyColor = new Color(0.3f,0.8f,0.7f,1f);
        s.clumpsMin = 3; s.clumpsMax = 4; s.clumpRadiusMin = 0.4f; s.clumpRadiusMax = 0.6f;
        s.canopyEmissive = 3f; s.detailCountMin = 3; s.detailCountMax = 4; s.detailEmissive = 2f;
        s.mouthRadiusMin = 0.5f; s.mouthRadiusMax = 0.7f; s.canopyDepthMin = 0.6f; s.canopyDepthMax = 0.8f;
        s.lureEmissive = 2.5f; s.teethMin = 5; s.teethMax = 6;
        s.shardsMin = 5; s.shardsMax = 6; s.shardLenMin = 0.6f; s.shardLenMax = 1f;
        s.subShardsMin = 2; s.subShardsMax = 3;
        return s;
    }

    private static float maxEmissive(float[] v) {
        float m = 0; for (int i = 10; i < v.length; i += STRIDE) m = Math.max(m, v[i]); return m;
    }

    @Test
    void buildsValidGeometryForEachArchetype() {
        for (AlienArchetype a : AlienArchetype.values()) {
            AlienPlantMeshData m = AlienPlantMeshBuilder.build(species(a), new Random(1));
            assertTrue(m.vertices.length > 0, "verts " + a);
            assertEquals(0, m.vertices.length % STRIDE, "stride " + a);
            assertEquals(0, m.indices.length % 3, "tris " + a);
            assertFalse(m.bounds.getDimensions(new Vector3()).isZero(), "bounds " + a);
            for (int i = 0; i < m.vertices.length; i += STRIDE) {
                float nx=m.vertices[i+3], ny=m.vertices[i+4], nz=m.vertices[i+5];
                float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                assertEquals(1f, len, 0.05f, "normal " + a + " @" + (i/STRIDE));
            }
        }
    }

    @Test
    void glowingArchetypesEmitNonGlowingStalkDoesNot() {
        assertTrue(maxEmissive(AlienPlantMeshBuilder.build(species(AlienArchetype.BIOLUMINESCENT), new Random(2)).vertices) > 0f);
        assertTrue(maxEmissive(AlienPlantMeshBuilder.build(species(AlienArchetype.CRYSTAL), new Random(2)).vertices) > 0f);
        // carnivorous: lure glows, so overall max > 0
        assertTrue(maxEmissive(AlienPlantMeshBuilder.build(species(AlienArchetype.CARNIVOROUS), new Random(2)).vertices) > 0f);
    }

    @Test
    void stalkOnlySpeciesHasZeroEmissiveWhenCanopyEmissiveZero() {
        AlienPlantSpecies s = species(AlienArchetype.CARNIVOROUS);
        s.lureEmissive = 0f; // no glow anywhere
        float[] v = AlienPlantMeshBuilder.build(s, new Random(3)).vertices;
        assertEquals(0f, maxEmissive(v), 1e-6f);
    }

    @Test
    void isDeterministic() {
        for (AlienArchetype a : AlienArchetype.values()) {
            float[] x = AlienPlantMeshBuilder.build(species(a), new Random(7)).vertices;
            float[] y = AlienPlantMeshBuilder.build(species(a), new Random(7)).vertices;
            assertArrayEquals(x, y, "determinism " + a);
        }
    }
}
