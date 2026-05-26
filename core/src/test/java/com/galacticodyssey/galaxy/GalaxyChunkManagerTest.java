package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyChunkManagerTest {

    private GalaxyChunkManager manager;
    private GalaxyConfig config;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        config.targetStarCount = 10000;
        config.radiusLY = 5000f;
        config.chunkSizeLY = 100f;
        config.maxLoadedChunks = 16;
        long starDomain = SeedDeriver.starDomain(42L);
        manager = new GalaxyChunkManager(config, starDomain);
    }

    @Test
    void sameChunkCoordinatesProduceSameStars() {
        GalaxyChunk a = manager.getOrGenerate(0, 0);
        long starDomain = SeedDeriver.starDomain(42L);
        GalaxyChunkManager manager2 = new GalaxyChunkManager(config, starDomain);
        GalaxyChunk b = manager2.getOrGenerate(0, 0);

        assertEquals(a.stars.size, b.stars.size);
        for (int i = 0; i < a.stars.size; i++) {
            assertEquals(a.stars.get(i).uniqueId, b.stars.get(i).uniqueId);
            assertEquals(a.stars.get(i).x, b.stars.get(i).x, 1e-6);
            assertEquals(a.stars.get(i).y, b.stars.get(i).y, 1e-6);
            assertEquals(a.stars.get(i).z, b.stars.get(i).z, 1e-6);
        }
    }

    @Test
    void differentChunkCoordinatesProduceDifferentStars() {
        GalaxyChunk a = manager.getOrGenerate(0, 0);
        GalaxyChunk b = manager.getOrGenerate(5, 5);
        if (a.stars.size > 0 && b.stars.size > 0) {
            assertNotEquals(a.stars.get(0).uniqueId, b.stars.get(0).uniqueId);
        }
    }

    @Test
    void starsAreWithinChunkBounds() {
        int cx = 3, cy = 4;
        GalaxyChunk chunk = manager.getOrGenerate(cx, cy);
        double minX = cx * config.chunkSizeLY;
        double maxX = (cx + 1) * config.chunkSizeLY;
        double minY = cy * config.chunkSizeLY;
        double maxY = (cy + 1) * config.chunkSizeLY;
        for (StarPosition star : chunk.stars) {
            assertTrue(star.x >= minX && star.x < maxX,
                "Star x=" + star.x + " outside chunk bounds [" + minX + "," + maxX + ")");
            assertTrue(star.y >= minY && star.y < maxY,
                "Star y=" + star.y + " outside chunk bounds [" + minY + "," + maxY + ")");
        }
    }

    @Test
    void chunkOutsideGalaxyDiskHasNoStars() {
        int farCX = (int)(config.radiusLY * 2 / config.chunkSizeLY);
        GalaxyChunk chunk = manager.getOrGenerate(farCX, farCX);
        assertEquals(0, chunk.stars.size);
    }

    @Test
    void getOrGenerateCachesChunks() {
        GalaxyChunk a = manager.getOrGenerate(1, 1);
        GalaxyChunk b = manager.getOrGenerate(1, 1);
        assertSame(a, b);
    }

    @Test
    void unloadDistantChunksRemovesChunks() {
        manager.getOrGenerate(0, 0);
        manager.getOrGenerate(100, 100);
        manager.unloadDistantChunks(0, 0, 500);
        assertEquals(1, manager.getLoadedChunkCount());
    }

    @Test
    void evictsWhenOverCapacity() {
        for (int i = 0; i < config.maxLoadedChunks + 5; i++) {
            manager.getOrGenerate(i, 0);
        }
        assertTrue(manager.getLoadedChunkCount() <= config.maxLoadedChunks);
    }

    @Test
    void coreChunksHaveStars() {
        GalaxyChunk core = manager.getOrGenerate(0, 0);
        assertTrue(core.stars.size > 0, "Core chunk at (0,0) should have stars");
    }
}
