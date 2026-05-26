package com.galacticodyssey.galaxy;

public class GalaxyRegionClassifier {

    public GalaxyRegion classify(double x, double y, GalaxyConfig cfg) {
        float nx = (float)(x / cfg.radiusLY);
        float ny = (float)(y / cfg.radiusLY);
        float r = (float) Math.sqrt(nx * nx + ny * ny);

        if (r > 1.0f) return GalaxyRegion.VOID;
        if (r < 0.1f) return GalaxyRegion.CORE;
        if (r < 0.4f) return GalaxyRegion.INNER_RIM;
        return GalaxyRegion.OUTER_RIM;
    }
}
