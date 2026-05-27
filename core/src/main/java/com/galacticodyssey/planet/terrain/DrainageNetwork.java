package com.galacticodyssey.planet.terrain;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Computes a drainage network on a 2D heightmap using D8 flow direction.
 * Fills depressions (sinks) using priority-flood, then extracts rivers
 * where flow accumulation exceeds a threshold.
 */
public final class DrainageNetwork {

    private DrainageNetwork() {}

    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};

    public static final class Result {
        public final int[] flowDirection;
        public final float[] flowAccumulation;
        public final boolean[] isRiver;
        public final boolean[] isLake;

        Result(int[] flowDirection, float[] flowAccumulation,
               boolean[] isRiver, boolean[] isLake) {
            this.flowDirection = flowDirection;
            this.flowAccumulation = flowAccumulation;
            this.isRiver = isRiver;
            this.isLake = isLake;
        }
    }

    public static Result compute(float[] heightmap, int width, int height, float riverThreshold) {
        int size = width * height;
        boolean[] isLake = new boolean[size];

        // Step 1: Fill sinks using priority-flood
        float[] filled = priorityFlood(heightmap, width, height, isLake);

        // Step 2: Compute D8 flow directions on filled heightmap
        int[] flowDir = new int[size];
        Arrays.fill(flowDir, -1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                float h = filled[idx];
                float steepest = 0f;
                int bestDir = -1;
                for (int d = 0; d < 8; d++) {
                    int nx = x + DX[d];
                    int ny = y + DY[d];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    float nh = filled[ny * width + nx];
                    float drop = h - nh;
                    if (drop > steepest) {
                        steepest = drop;
                        bestDir = d;
                    }
                }
                flowDir[idx] = bestDir;
            }
        }

        // Step 3: Flow accumulation (topological sort by height descending)
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Float.compare(filled[b], filled[a]));

        float[] accumulation = new float[size];
        for (int idx : order) {
            accumulation[idx] += 1f;
            int dir = flowDir[idx];
            if (dir >= 0) {
                int x = idx % width;
                int y = idx / width;
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    accumulation[ny * width + nx] += accumulation[idx];
                }
            }
        }

        // Step 4: Extract rivers
        boolean[] isRiver = new boolean[size];
        for (int i = 0; i < size; i++) {
            isRiver[i] = accumulation[i] > riverThreshold;
        }

        return new Result(flowDir, accumulation, isRiver, isLake);
    }

    private static float[] priorityFlood(float[] heightmap, int width, int height, boolean[] isLake) {
        int size = width * height;
        float[] filled = Arrays.copyOf(heightmap, size);
        boolean[] visited = new boolean[size];

        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> Float.compare(filled[a[0]], filled[b[0]]));

        // Seed with edge cells
        for (int x = 0; x < width; x++) {
            int topIdx = x;
            int botIdx = (height - 1) * width + x;
            visited[topIdx] = true;
            visited[botIdx] = true;
            queue.offer(new int[]{topIdx});
            queue.offer(new int[]{botIdx});
        }
        for (int y = 1; y < height - 1; y++) {
            int leftIdx = y * width;
            int rightIdx = y * width + width - 1;
            visited[leftIdx] = true;
            visited[rightIdx] = true;
            queue.offer(new int[]{leftIdx});
            queue.offer(new int[]{rightIdx});
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int idx = cell[0];
            int x = idx % width;
            int y = idx / width;
            float spillLevel = filled[idx];

            for (int d = 0; d < 8; d++) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                int nIdx = ny * width + nx;
                if (visited[nIdx]) continue;
                visited[nIdx] = true;

                if (filled[nIdx] < spillLevel) {
                    isLake[nIdx] = true;
                    filled[nIdx] = spillLevel;
                }
                queue.offer(new int[]{nIdx});
            }
        }

        return filled;
    }
}
