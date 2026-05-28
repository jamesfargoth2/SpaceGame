package com.galacticodyssey.hacking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD (step 1): Failing tests for PuzzleGrid. PuzzleGrid does not exist yet;
 * these tests are expected to fail at compile time until Task 5 is complete.
 */
class PuzzleGridTest {

    // -----------------------------------------------------------------------
    // Test 1
    // -----------------------------------------------------------------------

    @Test
    void sourceIsPoweredAtStart() {
        PuzzleGrid grid = new PuzzleGrid(3, 3);

        GridTile source = new GridTile(TileType.CROSS, 0);
        source.isSource = true;
        grid.setTile(1, 1, source);

        grid.propagatePower();

        assertTrue(grid.getTile(1, 1).powered,
                "Source tile must be powered after propagatePower()");
    }

    // -----------------------------------------------------------------------
    // Test 2
    // -----------------------------------------------------------------------

    @Test
    void powerDoesNotFlowThroughDisconnectedTiles() {
        // STRAIGHT at rotation=0 is N+S — no east/west openings
        PuzzleGrid grid = new PuzzleGrid(1, 3);

        GridTile source = new GridTile(TileType.STRAIGHT, 0);
        source.isSource = true;
        grid.setTile(0, 0, source);

        grid.setTile(0, 1, new GridTile(TileType.STRAIGHT, 0));

        GridTile target = new GridTile(TileType.STRAIGHT, 0);
        target.isTarget = true;
        grid.setTile(0, 2, target);

        grid.propagatePower();

        assertFalse(grid.getTile(0, 2).powered,
                "Power must not flow horizontally through N+S STRAIGHT tiles");
    }

    // -----------------------------------------------------------------------
    // Test 3
    // -----------------------------------------------------------------------

    @Test
    void powerFlowsAlongHorizontalPath() {
        // STRAIGHT at rotation=1 is E+W
        PuzzleGrid grid = new PuzzleGrid(1, 3);

        GridTile source = new GridTile(TileType.STRAIGHT, 1);
        source.isSource = true;
        grid.setTile(0, 0, source);

        grid.setTile(0, 1, new GridTile(TileType.STRAIGHT, 1));

        GridTile target = new GridTile(TileType.STRAIGHT, 1);
        target.isTarget = true;
        grid.setTile(0, 2, target);

        grid.propagatePower();

        assertTrue(grid.getTile(0, 2).powered,
                "Power must flow east along a row of E+W STRAIGHT tiles");
        assertTrue(grid.isWon(),
                "isWon() must return true when all targets are powered");
    }

    // -----------------------------------------------------------------------
    // Test 4
    // -----------------------------------------------------------------------

    @Test
    void rotateTileRecalculatesPowerAndDetectsWin() {
        PuzzleGrid grid = new PuzzleGrid(1, 2);

        GridTile source = new GridTile(TileType.STRAIGHT, 1); // E+W
        source.isSource = true;
        grid.setTile(0, 0, source);

        GridTile target = new GridTile(TileType.STRAIGHT, 0); // N+S — disconnected
        target.isTarget = true;
        grid.setTile(0, 1, target);

        grid.propagatePower();
        assertFalse(grid.isWon(),
                "isWon() must be false when target tile is not aligned (N+S vs E+W)");

        // Rotate target from rotation=0 (N+S) to rotation=1 (E+W) — now aligned
        grid.rotateTile(0, 1);

        assertTrue(grid.isWon(),
                "isWon() must be true after rotateTile() aligns the target and recalculates power");
    }

    // -----------------------------------------------------------------------
    // Test 5
    // -----------------------------------------------------------------------

    @Test
    void crossTileConnectsAllNeighbors() {
        PuzzleGrid grid = new PuzzleGrid(3, 3);

        GridTile source = new GridTile(TileType.STRAIGHT, 1); // E+W
        source.isSource = true;
        grid.setTile(1, 0, source);

        grid.setTile(1, 1, new GridTile(TileType.CROSS, 0)); // all 4 sides

        GridTile target = new GridTile(TileType.STRAIGHT, 1); // E+W
        target.isTarget = true;
        grid.setTile(1, 2, target);

        grid.propagatePower();

        assertTrue(grid.isWon(),
                "Power must flow through a CROSS tile and reach the target");
    }

    // -----------------------------------------------------------------------
    // Test 6
    // -----------------------------------------------------------------------

    @Test
    void elbowCornerRoutesCorrectly() {
        // L-shaped path: (0,0) → East → (0,1) corner → South → (1,1) target
        // ELBOW rotation=2: S+W (opens West to receive power, South to pass down)
        PuzzleGrid grid = new PuzzleGrid(2, 2);

        GridTile source = new GridTile(TileType.STRAIGHT, 1); // E+W
        source.isSource = true;
        grid.setTile(0, 0, source);

        // Corner: West receives from source, South passes to (1,1)
        GridTile corner = new GridTile(TileType.ELBOW, 2); // S+W
        grid.setTile(0, 1, corner);

        GridTile target = new GridTile(TileType.STRAIGHT, 0); // N+S
        target.isTarget = true;
        grid.setTile(1, 1, target);

        grid.propagatePower();

        assertTrue(grid.getTile(1, 1).powered,
                "Power must travel the L-shaped path through an ELBOW corner to the target");
        assertTrue(grid.isWon(),
                "isWon() must return true when the sole target is powered via elbow routing");
    }

    // -----------------------------------------------------------------------
    // Test 7
    // -----------------------------------------------------------------------

    @Test
    void isWonReturnsFalseWhenNoTargetsSet() {
        PuzzleGrid grid = new PuzzleGrid(3, 3);

        GridTile source = new GridTile(TileType.CROSS, 0);
        source.isSource = true;
        grid.setTile(1, 1, source);

        // Neighbouring CROSS tiles that are powered but not targets
        grid.setTile(0, 1, new GridTile(TileType.CROSS, 0));
        grid.setTile(1, 0, new GridTile(TileType.CROSS, 0));

        grid.propagatePower();

        assertFalse(grid.isWon(),
                "isWon() must return false when no tiles have isTarget=true");
    }
}
