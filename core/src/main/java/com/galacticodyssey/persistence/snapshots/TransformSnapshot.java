package com.galacticodyssey.persistence.snapshots;

public class TransformSnapshot {
    public double galaxyX;
    public double galaxyY;
    public double galaxyZ;
    public float rotX;
    public float rotY;
    public float rotZ;
    public float rotW;

    public TransformSnapshot() {}

    public TransformSnapshot(double gx, double gy, double gz,
                             float rx, float ry, float rz, float rw) {
        this.galaxyX = gx;
        this.galaxyY = gy;
        this.galaxyZ = gz;
        this.rotX = rx;
        this.rotY = ry;
        this.rotZ = rz;
        this.rotW = rw;
    }
}
