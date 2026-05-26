package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;

public class GalaxyChunk {

    public final ChunkKey key;
    public final Array<StarPosition> stars;
    public final double centreX;
    public final double centreY;
    public final float averageDensity;

    public GalaxyChunk(ChunkKey key, Array<StarPosition> stars,
                       double centreX, double centreY, float averageDensity) {
        this.key = key;
        this.stars = stars;
        this.centreX = centreX;
        this.centreY = centreY;
        this.averageDensity = averageDensity;
    }
}
