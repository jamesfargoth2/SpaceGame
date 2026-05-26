package com.galacticodyssey.planet.terrain;

public enum CubeFace {
    POS_X( 1, 0, 0),
    NEG_X(-1, 0, 0),
    POS_Y( 0, 1, 0),
    NEG_Y( 0,-1, 0),
    POS_Z( 0, 0, 1),
    NEG_Z( 0, 0,-1);

    public final int axisX;
    public final int axisY;
    public final int axisZ;

    CubeFace(int axisX, int axisY, int axisZ) {
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
    }
}
