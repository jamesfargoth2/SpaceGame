package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class GalaxyManager implements Disposable {

    private final long galaxySeed;
    private final GalaxyConfig config;
    private final GalaxyChunkManager chunkManager;
    private final GalaxyRegionClassifier regionClassifier;
    private final Array<NebulaRegion> nebulae;

    public GalaxyManager(long galaxySeed, GalaxyConfig config) {
        this.galaxySeed = galaxySeed;
        this.config = config;
        long starDomain = SeedDeriver.starDomain(galaxySeed);
        this.chunkManager = new GalaxyChunkManager(config, starDomain);
        this.regionClassifier = new GalaxyRegionClassifier();
        this.nebulae = new NebulaPlacer().place(config, galaxySeed);
    }

    public void updateView(double viewCentreX, double viewCentreY, float viewRadiusLY) {
        chunkManager.loadChunksAround(viewCentreX, viewCentreY, viewRadiusLY);
        chunkManager.unloadDistantChunks(viewCentreX, viewCentreY, viewRadiusLY * 2f);
    }

    public Iterable<StarPosition> getLoadedStars() {
        return () -> new StarIterator(chunkManager.getLoadedChunks().iterator());
    }

    public StarPosition findNearestStar(double x, double y) {
        int cx = (int) Math.floor(x / config.chunkSizeLY);
        int cy = (int) Math.floor(y / config.chunkSizeLY);

        StarPosition nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                GalaxyChunk chunk = chunkManager.getOrGenerate(cx + dx, cy + dy);
                for (StarPosition star : chunk.stars) {
                    double ddx = star.x - x;
                    double ddy = star.y - y;
                    double distSq = ddx * ddx + ddy * ddy;
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = star;
                    }
                }
            }
        }
        return nearest;
    }

    public GalaxyRegion getRegion(double x, double y) {
        return regionClassifier.classify(x, y, config);
    }

    public Array<NebulaRegion> getNebulae() {
        return nebulae;
    }

    public long getGalaxySeed() {
        return galaxySeed;
    }

    public GalaxyConfig getConfig() {
        return config;
    }

    @Override
    public void dispose() {
    }

    private static class StarIterator implements Iterator<StarPosition> {
        private final Iterator<GalaxyChunk> chunkIterator;
        private int starIndex;
        private GalaxyChunk currentChunk;

        StarIterator(Iterator<GalaxyChunk> chunkIterator) {
            this.chunkIterator = chunkIterator;
            this.starIndex = 0;
            advance();
        }

        private void advance() {
            while (currentChunk == null || starIndex >= currentChunk.stars.size) {
                if (!chunkIterator.hasNext()) {
                    currentChunk = null;
                    return;
                }
                currentChunk = chunkIterator.next();
                starIndex = 0;
            }
        }

        @Override
        public boolean hasNext() {
            return currentChunk != null && starIndex < currentChunk.stars.size;
        }

        @Override
        public StarPosition next() {
            if (!hasNext()) throw new NoSuchElementException();
            StarPosition star = currentChunk.stars.get(starIndex);
            starIndex++;
            advance();
            return star;
        }
    }
}
