package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.utils.FloatArray;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Manages the set of grass cells active around the camera, caches their instance data,
 *  and packs the active set into one contiguous instance buffer. GL-free. */
public final class GrassField {
    private static final long GRASS_SALT = 0x6772617373L; // "grass"

    private final GrassConfig config;
    private final TerrainSampler sampler;
    private final long grassSeed;

    /** LRU cache of cell key -> packed instance data. */
    private final LinkedHashMap<Long, float[]> cache;
    private Set<Long> activeKeys = new HashSet<>();
    private float[] packed = new float[0];
    private int instanceCount;

    public GrassField(GrassConfig config, TerrainSampler sampler, long worldSeed) {
        this.config = config;
        this.sampler = sampler;
        this.grassSeed = SeedDeriver.forId(SeedDeriver.floraDomain(worldSeed), GRASS_SALT);
        final int cap = Math.max(16, config.maxCachedCells);
        this.cache = new LinkedHashMap<>(cap, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, float[]> e) {
                return size() > cap;
            }
        };
    }

    /** Recomputes the active cell set for the camera position. Returns true if it changed
     *  (and the packed buffer was rebuilt), false if nothing changed. */
    public boolean update(float camX, float camZ) {
        Set<Long> newKeys = computeActiveKeys(camX, camZ);
        if (newKeys.equals(activeKeys)) return false;
        activeKeys = newKeys;
        repack();
        return true;
    }

    Set<Long> computeActiveKeys(float camX, float camZ) {
        float cell = config.cellSize, radius = config.radius;
        int minX = (int) Math.floor((camX - radius) / cell);
        int maxX = (int) Math.floor((camX + radius) / cell);
        int minZ = (int) Math.floor((camZ - radius) / cell);
        int maxZ = (int) Math.floor((camZ + radius) / cell);
        float r2 = radius * radius;
        Set<Long> keys = new HashSet<>();
        for (int cz = minZ; cz <= maxZ; cz++) {
            for (int cx = minX; cx <= maxX; cx++) {
                float centreX = (cx + 0.5f) * cell, centreZ = (cz + 0.5f) * cell;
                float dx = centreX - camX, dz = centreZ - camZ;
                if (dx * dx + dz * dz <= r2) keys.add(key(cx, cz));
            }
        }
        return keys;
    }

    private void repack() {
        FloatArray buf = new FloatArray();
        for (Long k : activeKeys) {
            float[] cellData = cache.get(k);
            if (cellData == null) {
                cellData = GrassCell.generate(cellX(k), cellZ(k), config, sampler, grassSeed);
                cache.put(k, cellData);
            }
            buf.addAll(cellData);
        }
        packed = buf.toArray();
        instanceCount = packed.length / GrassCell.STRIDE;
    }

    public float[] instanceBuffer() { return packed; }
    public int instanceCount() { return instanceCount; }

    // --- cell key packing (two ints into a long) ---
    private static long key(int cx, int cz) { return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL); }
    private static int cellX(long k) { return (int) (k >> 32); }
    private static int cellZ(long k) { return (int) k; }
}
