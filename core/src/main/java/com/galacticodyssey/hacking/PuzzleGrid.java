package com.galacticodyssey.hacking;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A 2D grid of {@link GridTile} tiles used for the hacking circuit-puzzle mini-game.
 *
 * <p>Power propagates via BFS from the single source tile through any two adjacent tiles
 * whose connector openings face each other (e.g. the source's East opening meets the
 * neighbour's West opening). Tiles that receive power have their {@code powered} flag set.
 *
 * <p>The puzzle is won when every tile marked {@code isTarget} is powered and at least
 * one target tile exists.
 */
public class PuzzleGrid {

    private final int rows;
    private final int cols;
    private final GridTile[][] tiles;

    /**
     * Creates a grid of the given dimensions. All tiles are initialised to
     * {@link TileType#EMPTY} and are neither sources, targets, nor powered.
     *
     * @param rows number of rows
     * @param cols number of columns
     */
    public PuzzleGrid(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.tiles = new GridTile[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c] = new GridTile(TileType.EMPTY, 0);
            }
        }
    }

    /**
     * Replaces the tile at {@code (row, col)} with the given tile.
     *
     * @param row  row index (0-based)
     * @param col  column index (0-based)
     * @param tile the tile to place
     */
    public void setTile(int row, int col, GridTile tile) {
        tiles[row][col] = tile;
    }

    /**
     * Returns the tile at {@code (row, col)}.
     *
     * @param row row index (0-based)
     * @param col column index (0-based)
     * @return the tile at that position
     */
    public GridTile getTile(int row, int col) {
        return tiles[row][col];
    }

    /**
     * Rotates the tile at {@code (row, col)} clockwise by 90° then calls
     * {@link #propagatePower()} to recalculate the power state of the whole grid.
     *
     * @param row row index (0-based)
     * @param col column index (0-based)
     */
    public void rotateTile(int row, int col) {
        tiles[row][col].rotateClockwise();
        propagatePower();
    }

    /**
     * Runs BFS from the source tile to determine which tiles are powered.
     *
     * <ol>
     *   <li>All {@code powered} flags are cleared.</li>
     *   <li>The source tile (if any) is marked powered and enqueued.</li>
     *   <li>BFS expands to each cardinal neighbour whose connector openings are mutually
     *       aligned with the current tile.</li>
     * </ol>
     */
    public void propagatePower() {
        // Step 1: clear all powered flags
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c].powered = false;
            }
        }

        // Step 2: find the source tile
        int sourceRow = -1;
        int sourceCol = -1;
        outer:
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c].isSource) {
                    sourceRow = r;
                    sourceCol = c;
                    break outer;
                }
            }
        }

        if (sourceRow == -1) {
            return; // no source — nothing to propagate
        }

        // Step 3: BFS
        tiles[sourceRow][sourceCol].powered = true;
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sourceRow, sourceCol});

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int r = pos[0];
            int c = pos[1];
            GridTile current = tiles[r][c];

            // North neighbour (row - 1)
            if (r - 1 >= 0) {
                GridTile neighbour = tiles[r - 1][c];
                if (!neighbour.powered
                        && current.type.hasNorth(current.rotation)
                        && neighbour.type.hasSouth(neighbour.rotation)) {
                    neighbour.powered = true;
                    queue.add(new int[]{r - 1, c});
                }
            }

            // South neighbour (row + 1)
            if (r + 1 < rows) {
                GridTile neighbour = tiles[r + 1][c];
                if (!neighbour.powered
                        && current.type.hasSouth(current.rotation)
                        && neighbour.type.hasNorth(neighbour.rotation)) {
                    neighbour.powered = true;
                    queue.add(new int[]{r + 1, c});
                }
            }

            // East neighbour (col + 1)
            if (c + 1 < cols) {
                GridTile neighbour = tiles[r][c + 1];
                if (!neighbour.powered
                        && current.type.hasEast(current.rotation)
                        && neighbour.type.hasWest(neighbour.rotation)) {
                    neighbour.powered = true;
                    queue.add(new int[]{r, c + 1});
                }
            }

            // West neighbour (col - 1)
            if (c - 1 >= 0) {
                GridTile neighbour = tiles[r][c - 1];
                if (!neighbour.powered
                        && current.type.hasWest(current.rotation)
                        && neighbour.type.hasEast(neighbour.rotation)) {
                    neighbour.powered = true;
                    queue.add(new int[]{r, c - 1});
                }
            }
        }
    }

    /**
     * Returns {@code true} if at least one target tile exists and every target tile
     * is currently powered.
     *
     * @return {@code true} when the puzzle is solved
     */
    public boolean isWon() {
        boolean hasTargets = false;
        boolean allPowered = true;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GridTile tile = tiles[r][c];
                if (tile.isTarget) {
                    hasTargets = true;
                    if (!tile.powered) {
                        allPowered = false;
                    }
                }
            }
        }
        return hasTargets && allPowered;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
}
