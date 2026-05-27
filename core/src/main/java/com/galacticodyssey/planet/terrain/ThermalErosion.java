package com.galacticodyssey.planet.terrain;

/**
 * Thermal erosion simulation. Redistributes material from steep slopes
 * that exceed the talus angle, simulating rockfall and scree formation.
 */
public final class ThermalErosion {

    private ThermalErosion() {}

    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};

    public static void erode(float[] heightmap, int width, int height,
                             float talusAngle, int iterations) {
        float redistributionRate = 0.5f;

        for (int iter = 0; iter < iterations; iter++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;
                    float h = heightmap[idx];

                    float maxDiff = 0f;
                    int maxNeighborIdx = -1;

                    for (int n = 0; n < 8; n++) {
                        int nx = x + DX[n];
                        int ny = y + DY[n];
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;

                        int nIdx = ny * width + nx;
                        float diff = h - heightmap[nIdx];
                        if (diff > maxDiff) {
                            maxDiff = diff;
                            maxNeighborIdx = nIdx;
                        }
                    }

                    if (maxNeighborIdx >= 0 && maxDiff > talusAngle) {
                        float excess = maxDiff - talusAngle;
                        float transfer = excess * redistributionRate * 0.5f;
                        heightmap[idx] -= transfer;
                        heightmap[maxNeighborIdx] += transfer;
                    }
                }
            }
        }
    }
}
