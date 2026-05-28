package com.galacticodyssey.hacking;

import java.util.Random;

public final class PuzzleGridFactory {

    private PuzzleGridFactory() {}

    /**
     * Generates a solvable puzzle grid with an L-shaped path from (0,0) to (gridSize-1, gridSize-1).
     *
     * @param gridSize     dimension of the square grid (rows == cols == gridSize)
     * @param difficulty   1-5, used as a reference for skill assist cap
     * @param hackingSkill player's HACKING rank
     * @param rng          random source (caller provides for testability)
     */
    public static PuzzleGrid create(int gridSize, int difficulty, int hackingSkill, Random rng) {
        if (gridSize < 2) {
            throw new IllegalArgumentException("gridSize must be at least 2, got: " + gridSize);
        }
        PuzzleGrid grid = new PuzzleGrid(gridSize, gridSize);

        // --- Build L-path tile list ---
        // Row 0, cols 0..(gridSize-1): horizontal run east
        // Col (gridSize-1), rows 1..(gridSize-1): vertical run south
        // Total path length = gridSize + (gridSize - 1) = 2*gridSize - 1
        // path[i] = {row, col}
        int pathLen = 2 * gridSize - 1;
        int[][] path = new int[pathLen][2];
        for (int c = 0; c < gridSize; c++) {
            path[c][0] = 0;
            path[c][1] = c;
        }
        for (int r = 1; r < gridSize; r++) {
            path[gridSize - 1 + r][0] = r;
            path[gridSize - 1 + r][1] = gridSize - 1;
        }

        // --- Determine solved tile type and rotation for each path position ---
        int[] solvedRotations = new int[pathLen];
        TileType[] solvedTypes = new TileType[pathLen];
        int cornerIndex = gridSize - 1;  // index in path[] where the turn happens

        for (int i = 0; i < pathLen; i++) {
            if (i < cornerIndex) {
                // Horizontal segment: STRAIGHT E+W (rotation 1)
                solvedTypes[i] = TileType.STRAIGHT;
                solvedRotations[i] = 1;
            } else if (i == cornerIndex) {
                // Corner: connects West (from horizontal run) and South (into vertical run) — ELBOW S+W (rotation 2)
                solvedTypes[i] = TileType.ELBOW;
                solvedRotations[i] = 2;
            } else {
                // Vertical segment: STRAIGHT N+S (rotation 0)
                solvedTypes[i] = TileType.STRAIGHT;
                solvedRotations[i] = 0;
            }
        }

        // --- Place solved tiles on path ---
        for (int i = 0; i < pathLen; i++) {
            GridTile tile = new GridTile(solvedTypes[i], solvedRotations[i]);
            if (i == 0)              tile.isSource = true;
            if (i == pathLen - 1)   tile.isTarget = true;
            grid.setTile(path[i][0], path[i][1], tile);
        }

        // --- Fill non-path cells with random non-CROSS/EMPTY tiles at random rotations ---
        boolean[][] onPath = new boolean[gridSize][gridSize];
        for (int[] p : path) onPath[p[0]][p[1]] = true;
        TileType[] fillers = {TileType.STRAIGHT, TileType.ELBOW, TileType.TEE};
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (!onPath[r][c]) {
                    grid.setTile(r, c, new GridTile(fillers[rng.nextInt(3)], rng.nextInt(4)));
                }
            }
        }

        // --- Scramble intermediate path tiles (indices 1..pathLen-2), excluding source and target ---
        // Each tile gets a random rotation that is NOT its solved rotation
        for (int i = 1; i < pathLen - 1; i++) {
            int scrambled;
            do { scrambled = rng.nextInt(4); } while (scrambled == solvedRotations[i]);
            grid.getTile(path[i][0], path[i][1]).rotation = scrambled;
        }

        // --- Skill assists: pre-solve first N intermediate tiles ---
        // assists = clamp(hackingSkill - difficulty, 0, intermediateCount - 1)
        // Always leave at least 1 intermediate tile scrambled.
        int intermediateCount = pathLen - 2;  // excludes source (i=0) and target (i=pathLen-1)
        int assists = Math.min(
            Math.max(0, hackingSkill - difficulty),
            Math.max(0, intermediateCount - 1)
        );
        for (int i = 1; i <= assists; i++) {
            grid.getTile(path[i][0], path[i][1]).rotation = solvedRotations[i];
        }

        grid.propagatePower();
        return grid;
    }
}
