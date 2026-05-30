package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.PlanetCoordsKM;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainChunkLodTest {
    @Test
    void centerIsPlanetSpaceKmAtRadius() {
        double radiusKm = 6371.0;
        TerrainChunk root = new TerrainChunk(CubeFace.POS_Y, 0, 0f, 0f, 1f, 1f, radiusKm);
        assertEquals(0.0, root.centerPlanetKm.x(), 1e-6);
        assertEquals(radiusKm, root.centerPlanetKm.y(), 1e-6);
        assertEquals(0.0, root.centerPlanetKm.z(), 1e-6);
    }

    @Test
    void splitsWhenCameraIsCloseRelativeToArc() {
        double radiusKm = 6371.0;
        TerrainChunk root = new TerrainChunk(CubeFace.POS_Y, 0, 0f, 0f, 1f, 1f, radiusKm);
        PlanetCoordsKM near = new PlanetCoordsKM(0, radiusKm + 1.0, 0);
        assertTrue(root.shouldSplit(near));
        PlanetCoordsKM far = new PlanetCoordsKM(0, radiusKm * 6.0, 0);
        assertTrue(root.shouldMerge(far));
    }
}
