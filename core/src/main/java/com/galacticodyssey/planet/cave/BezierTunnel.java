package com.galacticodyssey.planet.cave;

/** A cubic Bezier tunnel connecting two cave chambers. */
public final class BezierTunnel {
    public final float startX, startY, startZ;
    public final float endX, endY, endZ;
    public final float ctrl1X, ctrl1Y, ctrl1Z;
    public final float ctrl2X, ctrl2Y, ctrl2Z;
    public final float startRadiusM;
    public final float endRadiusM;
    public final boolean isCollapsed;

    public BezierTunnel(float startX, float startY, float startZ,
                        float endX, float endY, float endZ,
                        float ctrl1X, float ctrl1Y, float ctrl1Z,
                        float ctrl2X, float ctrl2Y, float ctrl2Z,
                        float startRadiusM, float endRadiusM,
                        boolean isCollapsed) {
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.ctrl1X = ctrl1X;
        this.ctrl1Y = ctrl1Y;
        this.ctrl1Z = ctrl1Z;
        this.ctrl2X = ctrl2X;
        this.ctrl2Y = ctrl2Y;
        this.ctrl2Z = ctrl2Z;
        this.startRadiusM = startRadiusM;
        this.endRadiusM = endRadiusM;
        this.isCollapsed = isCollapsed;
    }
}
