package com.galacticodyssey.networking.prediction;

public class PredictedState {
    public final float posX, posY, posZ;
    public final float rotX, rotY, rotZ, rotW;
    public final float velX, velY, velZ;

    public PredictedState(float posX, float posY, float posZ,
                          float rotX, float rotY, float rotZ, float rotW,
                          float velX, float velY, float velZ) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.rotW = rotW;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
    }

    public float distanceTo(PredictedState other) {
        float dx = posX - other.posX;
        float dy = posY - other.posY;
        float dz = posZ - other.posZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
