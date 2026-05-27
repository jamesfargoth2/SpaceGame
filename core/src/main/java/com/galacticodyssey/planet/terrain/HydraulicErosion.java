package com.galacticodyssey.planet.terrain;

import java.util.Random;

/**
 * Particle-based hydraulic erosion simulation. Spawns water droplets that
 * flow downhill, eroding and depositing sediment based on local slope
 * and carrying capacity.
 */
public final class HydraulicErosion {

    private HydraulicErosion() {}

    private static final int DEFAULT_MAX_LIFETIME = 30;
    private static final float INERTIA = 0.05f;
    private static final float SEDIMENT_CAPACITY_FACTOR = 4f;
    private static final float MIN_SEDIMENT_CAPACITY = 0.01f;
    private static final float ERODE_SPEED = 0.3f;
    private static final float DEPOSIT_SPEED = 0.3f;
    private static final float EVAPORATE_SPEED = 0.01f;
    private static final float GRAVITY = 4f;
    private static final int EROSION_RADIUS = 3;

    public static void erode(float[] heightmap, int width, int height, long seed, int iterations) {
        Random rng = new Random(seed);
        float[][] brushWeights = precomputeBrush(EROSION_RADIUS);

        for (int iter = 0; iter < iterations; iter++) {
            float posX = rng.nextFloat() * (width - 1);
            float posY = rng.nextFloat() * (height - 1);
            float dirX = 0f;
            float dirY = 0f;
            float speed = 1f;
            float water = 1f;
            float sediment = 0f;

            for (int step = 0; step < DEFAULT_MAX_LIFETIME; step++) {
                int nodeX = (int) posX;
                int nodeY = (int) posY;
                float fracX = posX - nodeX;
                float fracY = posY - nodeY;

                float[] gradient = computeGradient(heightmap, width, height, nodeX, nodeY, fracX, fracY);
                float heightOld = gradient[2];

                dirX = dirX * INERTIA - gradient[0] * (1f - INERTIA);
                dirY = dirY * INERTIA - gradient[1] * (1f - INERTIA);

                float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
                if (len < 1e-6f) {
                    dirX = rng.nextFloat() * 2f - 1f;
                    dirY = rng.nextFloat() * 2f - 1f;
                    len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
                }
                dirX /= len;
                dirY /= len;

                float newPosX = posX + dirX;
                float newPosY = posY + dirY;

                if (newPosX < 0 || newPosX >= width - 1 || newPosY < 0 || newPosY >= height - 1) {
                    break;
                }

                float heightNew = sampleHeight(heightmap, width, height, newPosX, newPosY);
                float heightDiff = heightNew - heightOld;

                if (heightDiff > 0) {
                    float deposit = Math.min(sediment, heightDiff);
                    depositSediment(heightmap, width, height, nodeX, nodeY, fracX, fracY, deposit);
                    sediment -= deposit;
                } else {
                    float capacity = Math.max(MIN_SEDIMENT_CAPACITY,
                            -heightDiff * speed * water * SEDIMENT_CAPACITY_FACTOR);

                    if (sediment > capacity) {
                        float deposit = (sediment - capacity) * DEPOSIT_SPEED;
                        depositSediment(heightmap, width, height, nodeX, nodeY, fracX, fracY, deposit);
                        sediment -= deposit;
                    } else {
                        float erodeAmount = Math.min((capacity - sediment) * ERODE_SPEED, -heightDiff);
                        erodeWithBrush(heightmap, width, height, nodeX, nodeY, erodeAmount, brushWeights);
                        sediment += erodeAmount;
                    }
                }

                speed = (float) Math.sqrt(Math.max(0.01f, speed * speed + heightDiff * GRAVITY));
                water *= (1f - EVAPORATE_SPEED);
                posX = newPosX;
                posY = newPosY;
            }
        }
    }

    private static float[][] precomputeBrush(int radius) {
        int size = radius * 2 + 1;
        float[][] weights = new float[size][size];
        float sum = 0f;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= radius) {
                    float w = 1f - dist / radius;
                    weights[dy + radius][dx + radius] = w;
                    sum += w;
                }
            }
        }
        if (sum > 0f) {
            for (int dy = 0; dy < size; dy++) {
                for (int dx = 0; dx < size; dx++) {
                    weights[dy][dx] /= sum;
                }
            }
        }
        return weights;
    }

    private static float[] computeGradient(float[] map, int w, int h,
                                            int nodeX, int nodeY, float fx, float fy) {
        int x0 = Math.max(0, Math.min(w - 2, nodeX));
        int y0 = Math.max(0, Math.min(h - 2, nodeY));

        float h00 = map[y0 * w + x0];
        float h10 = map[y0 * w + x0 + 1];
        float h01 = map[(y0 + 1) * w + x0];
        float h11 = map[(y0 + 1) * w + x0 + 1];

        float gx = (h10 - h00) * (1f - fy) + (h11 - h01) * fy;
        float gy = (h01 - h00) * (1f - fx) + (h11 - h10) * fx;
        float height = h00 * (1f - fx) * (1f - fy) + h10 * fx * (1f - fy)
                + h01 * (1f - fx) * fy + h11 * fx * fy;

        return new float[]{gx, gy, height};
    }

    private static float sampleHeight(float[] map, int w, int h, float px, float py) {
        int x0 = Math.max(0, Math.min(w - 2, (int) px));
        int y0 = Math.max(0, Math.min(h - 2, (int) py));
        float fx = px - x0;
        float fy = py - y0;
        return map[y0 * w + x0] * (1f - fx) * (1f - fy)
                + map[y0 * w + x0 + 1] * fx * (1f - fy)
                + map[(y0 + 1) * w + x0] * (1f - fx) * fy
                + map[(y0 + 1) * w + x0 + 1] * fx * fy;
    }

    private static void depositSediment(float[] map, int w, int h,
                                         int nodeX, int nodeY, float fx, float fy,
                                         float amount) {
        int x0 = Math.max(0, Math.min(w - 2, nodeX));
        int y0 = Math.max(0, Math.min(h - 2, nodeY));
        map[y0 * w + x0] += amount * (1f - fx) * (1f - fy);
        map[y0 * w + x0 + 1] += amount * fx * (1f - fy);
        map[(y0 + 1) * w + x0] += amount * (1f - fx) * fy;
        map[(y0 + 1) * w + x0 + 1] += amount * fx * fy;
    }

    private static void erodeWithBrush(float[] map, int w, int h,
                                        int cx, int cy, float amount,
                                        float[][] brushWeights) {
        int radius = brushWeights.length / 2;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int px = cx + dx;
                int py = cy + dy;
                if (px >= 0 && px < w && py >= 0 && py < h) {
                    float weight = brushWeights[dy + radius][dx + radius];
                    map[py * w + px] -= amount * weight;
                }
            }
        }
    }
}
