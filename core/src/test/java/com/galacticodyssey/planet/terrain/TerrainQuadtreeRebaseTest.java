package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class TerrainQuadtreeRebaseTest {

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    private TerrainQuadtree newTree(PlanetCoordsKM origin) {
        double radiusKm = 6371.0;
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        return new TerrainQuadtree(radiusKm, origin, noise, biomeMap, null); // null world = headless
    }

    /** Drive the async quadtree until every visible leaf has had its placement applied. */
    private static void settle(TerrainQuadtree tree, PlanetCoordsKM camera) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            tree.update(camera);
            tree.pump();
            boolean allPlaced = tree.getVisibleLeaves().stream()
                .allMatch(l -> l.state == TerrainChunk.State.READY);
            if (allPlaced && !tree.getVisibleLeaves().isEmpty()) return;
            Thread.sleep(2);
        }
    }

    @Test
    void placementEqualsPlanetToLocalOfChunkCentre() throws InterruptedException {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        TerrainQuadtree tree = newTree(origin);
        settle(tree, new PlanetCoordsKM(0, 6371.001, 0)); // async: wait for builds, apply placement
        for (TerrainChunk leaf : tree.getVisibleLeaves()) {
            LocalCoordsM expected = com.galacticodyssey.core.coords.CoordConvert
                .planetToLocal(leaf.centerPlanetKm, origin);
            assertEquals(expected.x(), leaf.placementLocal.x(), 0.5f);
            assertEquals(expected.y(), leaf.placementLocal.y(), 0.5f);
            assertEquals(expected.z(), leaf.placementLocal.z(), 0.5f);
        }
    }

    @Test
    void rebaseShiftsEveryPlacementByTheSameDelta() throws InterruptedException {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        TerrainQuadtree tree = newTree(origin);
        settle(tree, new PlanetCoordsKM(0, 6371.001, 0)); // async: wait for builds, apply placement
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
