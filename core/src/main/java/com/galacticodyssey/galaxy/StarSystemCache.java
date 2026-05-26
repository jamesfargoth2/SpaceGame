package com.galacticodyssey.galaxy;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StarSystemCache {
    private final StarSystemGenerator generator;
    private final LinkedHashMap<Long, StarSystem> cache;

    public StarSystemCache(long galaxySeed, int maxSize) {
        this.generator = new StarSystemGenerator(galaxySeed);
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, StarSystem> eldest) {
                return size() > maxSize;
            }
        };
    }

    public StarSystem get(StarPosition star, GalaxyRegion region) {
        StarSystem cached = cache.get(star.uniqueId);
        if (cached != null) return cached;
        StarSystem system = generator.generate(star, region);
        cache.put(star.uniqueId, system);
        return system;
    }
}
