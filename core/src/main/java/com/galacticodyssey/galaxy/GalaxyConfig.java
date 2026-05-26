package com.galacticodyssey.galaxy;

public class GalaxyConfig {
    public GalaxyType type = GalaxyType.SPIRAL;
    public int targetStarCount = 1000000;
    public float radiusLY = 50000f;
    public int armCount = 4;
    public float armWindingAngle = 4.0f;
    public float armWidth = 0.15f;
    public float coreDensityFactor = 3.0f;
    public int nebulaCount = 200;
    public float chunkSizeLY = 100f;
    public int maxLoadedChunks = 512;

    public static GalaxyConfig defaults() {
        return new GalaxyConfig();
    }
}
