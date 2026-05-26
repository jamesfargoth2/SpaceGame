package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;
import java.util.List;

public class InteriorLayout {
    public final List<RoomPlacement> rooms;
    public final boolean[][][] corridorCells;
    public final Vector3 airlockPosition;
    public final Vector3 pilotSeatPosition;
    public final float[] floorVertices;
    public final short[] floorIndices;
    public final float[] wallVertices;
    public final short[] wallIndices;
    public final int gridSizeX, gridSizeY, gridSizeZ;

    public InteriorLayout(List<RoomPlacement> rooms, boolean[][][] corridorCells,
                          Vector3 airlockPosition, Vector3 pilotSeatPosition,
                          float[] floorVertices, short[] floorIndices,
                          float[] wallVertices, short[] wallIndices,
                          int gridSizeX, int gridSizeY, int gridSizeZ) {
        this.rooms = rooms;
        this.corridorCells = corridorCells;
        this.airlockPosition = airlockPosition;
        this.pilotSeatPosition = pilotSeatPosition;
        this.floorVertices = floorVertices;
        this.floorIndices = floorIndices;
        this.wallVertices = wallVertices;
        this.wallIndices = wallIndices;
        this.gridSizeX = gridSizeX;
        this.gridSizeY = gridSizeY;
        this.gridSizeZ = gridSizeZ;
    }
}
