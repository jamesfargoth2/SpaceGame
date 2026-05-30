package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class TerrainQuadtreeStreamingTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private TerrainQuadtree tree() {
        TerrainNoiseStack noise = new TerrainNoiseStack(5L);
        BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
        return new TerrainQuadtree(6371.0, PlanetCoordsKM.ORIGIN, noise, biome, null);
    }

    @Test
    void residentLeafCountStaysBounded() {
        TerrainQuadtree tree = tree();
        PlanetCoordsKM cam = new PlanetCoordsKM(0, 6371.0 + 0.01, 0);
        for (int frame = 0; frame < 400; frame++) {
            tree.update(cam);
            tree.pump();
        }
        int leaves = tree.getVisibleLeaves().size();
        assertTrue(leaves > 0 && leaves < 4000, "resident leaves should be bounded, was " + leaves);
    }

    @Test
    void onlyNearDeepLeavesRequestCollision() {
        TerrainQuadtree tree = tree();
        PlanetCoordsKM cam = new PlanetCoordsKM(0, 6371.0 + 0.01, 0);
        for (int frame = 0; frame < 400; frame++) { tree.update(cam); tree.pump(); }
        long collisionWanted = tree.getVisibleLeaves().stream().filter(c -> c.wantsCollision).count();
        assertTrue(collisionWanted > 0 && collisionWanted < 256,
            "collision-wanting leaves should be a bounded shell, was " + collisionWanted);
    }

    @Test
    void renderableLeavesExcludeFarSideChunks() {
        TerrainQuadtree tree = tree();
        PlanetCoordsKM cam = new PlanetCoordsKM(0, 6371.0 + 0.01, 0); // +Y pole
        for (int frame = 0; frame < 400; frame++) { tree.update(cam); tree.pump(); }
        // Rebase the floating origin to the player position (as the real game does via onOriginRebased),
        // so that near chunks have small local magnitude and far-hemisphere chunks have large magnitude.
        tree.setOrigin(cam);

        java.util.List<TerrainChunk> all = new java.util.ArrayList<>(tree.getVisibleLeaves());
        java.util.List<TerrainChunk> renderable = new java.util.ArrayList<>(tree.getRenderableLeaves());
        assertTrue(renderable.size() <= all.size());
        assertTrue(renderable.size() > 0, "near-field chunks must remain renderable");
        for (var c : renderable) {
            float mag = c.placementLocal.toVector3().len();
            assertTrue(mag <= TerrainQuadtree.RENDER_BAND_M, "renderable chunk beyond band: " + mag);
        }
        boolean someFarExcluded = all.size() > renderable.size();
        assertTrue(someFarExcluded, "far-side chunks should be culled from rendering");
    }
}
