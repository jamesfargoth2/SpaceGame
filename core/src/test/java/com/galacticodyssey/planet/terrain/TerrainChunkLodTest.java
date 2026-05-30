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

    @Test
    void finestVertexSpacingIsWalkable() {
        // At MAX_DEPTH on an Earth-radius planet, leaf vertex spacing should be ~10-25 m.
        double radiusKm = 6371.0;
        // Arc length of a full face ≈ radius * (pi/2). A depth-d chunk spans 1/2^d of the face.
        double faceArcM = radiusKm * 1000.0 * (Math.PI / 2.0);
        double chunkArcM = faceArcM / Math.pow(2, TerrainChunk.MAX_DEPTH);
        double vertexSpacingM = chunkArcM / (com.galacticodyssey.planet.terrain.TerrainMeshBuilder.GRID_SIZE - 1);
        assertTrue(vertexSpacingM > 8.0 && vertexSpacingM < 30.0,
            "finest vertex spacing was " + vertexSpacingM + " m");
    }

    @Test
    void generationTokenBumpsOnInvalidate() {
        TerrainChunk c = new TerrainChunk(CubeFace.POS_Z, 0, 0f, 0f, 1f, 1f, 6371.0);
        int t0 = c.generationToken();
        c.invalidate();
        assertNotEquals(t0, c.generationToken());
    }
}
