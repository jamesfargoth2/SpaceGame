package com.galacticodyssey.networking.interpolation;

public class EntitySnapshot {
    public final int tick;
    public final float posX, posY, posZ;
    public final float rotX, rotY, rotZ, rotW;
    public final float velX, velY, velZ;

    public EntitySnapshot(int tick,
                          float posX, float posY, float posZ,
                          float rotX, float rotY, float rotZ, float rotW,
                          float velX, float velY, float velZ) {
        this.tick = tick;
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

    public static EntitySnapshot lerp(EntitySnapshot a, EntitySnapshot b, float t) {
        float px = a.posX + (b.posX - a.posX) * t;
        float py = a.posY + (b.posY - a.posY) * t;
        float pz = a.posZ + (b.posZ - a.posZ) * t;

        // nlerp for rotation (fast approximation of slerp)
        float dot = a.rotX * b.rotX + a.rotY * b.rotY + a.rotZ * b.rotZ + a.rotW * b.rotW;
        float sign = dot < 0 ? -1f : 1f;
        float rx = a.rotX + (sign * b.rotX - a.rotX) * t;
        float ry = a.rotY + (sign * b.rotY - a.rotY) * t;
        float rz = a.rotZ + (sign * b.rotZ - a.rotZ) * t;
        float rw = a.rotW + (sign * b.rotW - a.rotW) * t;
        float invLen = 1f / (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
        rx *= invLen;
        ry *= invLen;
        rz *= invLen;
        rw *= invLen;

        float vx = a.velX + (b.velX - a.velX) * t;
        float vy = a.velY + (b.velY - a.velY) * t;
        float vz = a.velZ + (b.velZ - a.velZ) * t;

        int tick = (int) (a.tick + (b.tick - a.tick) * t);
        return new EntitySnapshot(tick, px, py, pz, rx, ry, rz, rw, vx, vy, vz);
    }

    public EntitySnapshot extrapolate(float seconds, float tickInterval) {
        float ticks = seconds / tickInterval;
        float px = posX + velX * seconds;
        float py = posY + velY * seconds;
        float pz = posZ + velZ * seconds;
        return new EntitySnapshot((int) (tick + ticks), px, py, pz,
                rotX, rotY, rotZ, rotW, velX, velY, velZ);
    }
}
