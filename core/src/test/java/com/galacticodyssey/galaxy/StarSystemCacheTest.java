package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarSystemCacheTest {
    private static final long GALAXY_SEED = 42L;
    private StarSystemCache cache;

    @BeforeEach
    void setUp() {
        cache = new StarSystemCache(GALAXY_SEED, 4);
    }

    @Test
    void cacheReturnsSameInstance() {
        StarPosition pos = makeStarPosition(1L);
        StarSystem a = cache.get(pos, GalaxyRegion.INNER_RIM);
        StarSystem b = cache.get(pos, GalaxyRegion.INNER_RIM);
        assertSame(a, b);
    }

    @Test
    void cacheEvictsLeastRecentlyUsed() {
        StarPosition p1 = makeStarPosition(1L);
        StarPosition p2 = makeStarPosition(2L);
        StarPosition p3 = makeStarPosition(3L);
        StarPosition p4 = makeStarPosition(4L);
        StarPosition p5 = makeStarPosition(5L);

        StarSystem s1 = cache.get(p1, GalaxyRegion.INNER_RIM);
        cache.get(p2, GalaxyRegion.INNER_RIM);
        cache.get(p3, GalaxyRegion.INNER_RIM);
        cache.get(p4, GalaxyRegion.INNER_RIM);

        // p1 is LRU, adding p5 should evict it
        cache.get(p5, GalaxyRegion.INNER_RIM);
        StarSystem s1Again = cache.get(p1, GalaxyRegion.INNER_RIM);
        assertNotSame(s1, s1Again, "p1 should have been evicted and regenerated");
    }

    @Test
    void cachedResultIsDeterministic() {
        StarPosition pos = makeStarPosition(42L);
        StarSystem a = cache.get(pos, GalaxyRegion.CORE);
        StarSystemCache cache2 = new StarSystemCache(GALAXY_SEED, 4);
        StarSystem b = cache2.get(pos, GalaxyRegion.CORE);
        assertEquals(a.spectralClass, b.spectralClass);
        assertEquals(a.temperature, b.temperature, 1e-6f);
    }

    private StarPosition makeStarPosition(long id) {
        StarPosition sp = new StarPosition();
        sp.uniqueId = id;
        sp.x = id * 100.0;
        sp.y = id * 50.0;
        sp.z = 0.0;
        sp.localDensity = 0.5f;
        return sp;
    }
}
