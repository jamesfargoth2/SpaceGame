package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Component;

/**
 * A 2D fuel-load grid over the active surface scene (local-scene coordinates).
 * Cell states: 0 = UNBURNT, 1 = IGNITING, 2 = BURNING, 3 = BURNT.
 */
public class FuelGridComponent implements Component {
    public static final byte UNBURNT = 0, IGNITING = 1, BURNING = 2, BURNT = 3;

    public int width;
    public int height;
    public float cellSize;     // m per cell
    public float originX;      // local-scene world X of cell (0,0) corner
    public float originZ;      // local-scene world Z of cell (0,0) corner

    public float[] fuelLoad;       // J of fuel per cell
    public float[] moisture;       // 0..1 (raises ignition threshold)
    public byte[] state;
    public float[] ignitionProgress; // accumulates toward 1.0 -> ignites
    public float[] burnTimer;      // seconds a cell has been burning

    public FuelGridComponent(int width, int height, float cellSize, float originX, float originZ) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.originX = originX;
        this.originZ = originZ;
        int n = width * height;
        this.fuelLoad = new float[n];
        this.moisture = new float[n];
        this.state = new byte[n];
        this.ignitionProgress = new float[n];
        this.burnTimer = new float[n];
    }

    public int index(int x, int y) { return y * width + x; }
    public boolean inBounds(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    public int cellX(float worldX) { return (int) Math.floor((worldX - originX) / cellSize); }
    public int cellY(float worldZ) { return (int) Math.floor((worldZ - originZ) / cellSize); }
    public float cellCenterX(int x) { return originX + (x + 0.5f) * cellSize; }
    public float cellCenterZ(int y) { return originZ + (y + 0.5f) * cellSize; }
}
