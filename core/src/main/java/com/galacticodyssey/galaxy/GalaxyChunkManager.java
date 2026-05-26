package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class GalaxyChunkManager {

    private final Map<ChunkKey, GalaxyChunk> loaded = new LinkedHashMap<>(16, 0.75f, true);
    private final GalaxyConfig config;
    private final GalaxyDensityField densityField;
    private final long starDomainSeed;

    public GalaxyChunkManager(GalaxyConfig config, long starDomainSeed) {
        this.config = config;
        this.starDomainSeed = starDomainSeed;
        this.densityField = new GalaxyDensityField();
    }

    public GalaxyChunk getOrGenerate(int cx, int cy) {
        ChunkKey key = new ChunkKey(cx, cy);
        GalaxyChunk cached = loaded.get(key);
        if (cached != null) return cached;

        GalaxyChunk chunk = generateChunk(cx, cy);
        loaded.put(key, chunk);
        evictIfOverCapacity();
        return chunk;
    }

    private GalaxyChunk generateChunk(int cx, int cy) {
        long chunkSeed = SeedDeriver.forChunk(starDomainSeed, cx, cy);
        Random rng = new Random(chunkSeed);
        GalaxyNoise noise = new GalaxyNoise(chunkSeed);

        double chunkWorldX = (double) cx * config.chunkSizeLY;
        double chunkWorldY = (double) cy * config.chunkSizeLY;

        float centreNX = (float) ((chunkWorldX + config.chunkSizeLY * 0.5) / config.radiusLY);
        float centreNY = (float) ((chunkWorldY + config.chunkSizeLY * 0.5) / config.radiusLY);

        if (centreNX * centreNX + centreNY * centreNY > 1.5f * 1.5f) {
            return new GalaxyChunk(new ChunkKey(cx, cy), new Array<>(0),
                chunkWorldX + config.chunkSizeLY * 0.5,
                chunkWorldY + config.chunkSizeLY * 0.5, 0f);
        }

        float avgDensity = densityField.density(centreNX, centreNY, config, noise);

        float chunkArea = config.chunkSizeLY * config.chunkSizeLY;
        float galaxyArea = MathUtils.PI * config.radiusLY * config.radiusLY;
        int expectedStars = (int)(config.targetStarCount * (chunkArea / galaxyArea) * avgDensity * 4f);

        Array<StarPosition> stars = new Array<>();
        int maxAttempts = Math.max(expectedStars * 5, 1);
        int attempts = 0;

        while (stars.size < expectedStars && attempts < maxAttempts) {
            attempts++;
            double localX = rng.nextFloat() * config.chunkSizeLY;
            double localY = rng.nextFloat() * config.chunkSizeLY;

            double worldX = chunkWorldX + localX;
            double worldY = chunkWorldY + localY;
            float nx = (float) (worldX / config.radiusLY);
            float ny = (float) (worldY / config.radiusLY);

            if (nx * nx + ny * ny > 1f) continue;

            float d = densityField.density(nx, ny, config, noise);
            if (rng.nextFloat() > d) continue;

            float r = (float) Math.sqrt(nx * nx + ny * ny);
            float zScale = 0.02f + 0.01f * (1f - r);
            float z = (float)(rng.nextGaussian() * zScale) * config.radiusLY;

            StarPosition star = new StarPosition();
            star.uniqueId = SeedDeriver.forId(chunkSeed, stars.size);
            star.x = worldX;
            star.y = worldY;
            star.z = z;
            star.localDensity = d;
            stars.add(star);
        }

        return new GalaxyChunk(new ChunkKey(cx, cy), stars,
            chunkWorldX + config.chunkSizeLY * 0.5,
            chunkWorldY + config.chunkSizeLY * 0.5,
            avgDensity);
    }

    public void loadChunksAround(double viewCentreX, double viewCentreY, float radiusLY) {
        int minCX = (int) Math.floor((viewCentreX - radiusLY) / config.chunkSizeLY);
        int maxCX = (int) Math.ceil((viewCentreX + radiusLY) / config.chunkSizeLY);
        int minCY = (int) Math.floor((viewCentreY - radiusLY) / config.chunkSizeLY);
        int maxCY = (int) Math.ceil((viewCentreY + radiusLY) / config.chunkSizeLY);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cy = minCY; cy <= maxCY; cy++) {
                getOrGenerate(cx, cy);
            }
        }
    }

    public void unloadDistantChunks(double viewCentreX, double viewCentreY, float unloadRadiusLY) {
        loaded.entrySet().removeIf(e -> {
            GalaxyChunk c = e.getValue();
            double dx = c.centreX - viewCentreX;
            double dy = c.centreY - viewCentreY;
            return Math.sqrt(dx * dx + dy * dy) > unloadRadiusLY;
        });
    }

    public Iterable<GalaxyChunk> getLoadedChunks() {
        return loaded.values();
    }

    public int getLoadedChunkCount() {
        return loaded.size();
    }

    private void evictIfOverCapacity() {
        while (loaded.size() > config.maxLoadedChunks) {
            Iterator<ChunkKey> it = loaded.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }
}
