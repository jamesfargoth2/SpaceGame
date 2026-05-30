package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class TerrainQuadtreeRebaseTest {
    private TerrainQuadtree newTree(PlanetCoordsKM origin) {
        double radiusKm = 6371.0;
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        return new TerrainQuadtree(radiusKm, origin, noise, biomeMap, null); // null world = headless
    }

    @Test
    void placementEqualsPlanetToLocalOfChunkCentre() {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        TerrainQuadtree tree = newTree(origin);
        tree.update(new PlanetCoordsKM(0, 6371.001, 0));
        for (TerrainChunk leaf : tree.getVisibleLeaves()) {
            LocalCoordsM expected = com.galacticodyssey.core.coords.CoordConvert
                .planetToLocal(leaf.centerPlanetKm, origin);
            assertEquals(expected.x(), leaf.placementLocal.x(), 0.5f);
            assertEquals(expected.y(), leaf.placementLocal.y(), 0.5f);
            assertEquals(expected.z(), leaf.placementLocal.z(), 0.5f);
        }
    }

    @Test
    void rebaseShiftsEveryPlacementByTheSameDelta() {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        TerrainQuadtree tree = newTree(origin);
        tree.update(new PlanetCoordsKM(0, 6371.001, 0));
        java.util.Map<TerrainChunk, float[]> before = new java.util.HashMap<>();
        for (TerrainChunk leaf : tree.getVisibleLeaves())
            before.put(leaf, new float[]{leaf.placementLocal.x(), leaf.placementLocal.y(), leaf.placementLocal.z()});
        PlanetCoordsKM newOrigin = new PlanetCoordsKM(0.5, 6371.0, 0);
        tree.setOrigin(newOrigin);
        for (TerrainChunk leaf : tree.getVisibleLeaves()) {
            float[] b = before.get(leaf);
            assertNotNull(b);
            assertEquals(b[0] - 500f, leaf.placementLocal.x(), 1.0f);
            assertEquals(b[1], leaf.placementLocal.y(), 1.0f);
            assertEquals(b[2], leaf.placementLocal.z(), 1.0f);
        }
    }
}
