package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;

public final class CubeSphere {

    public static Vector3 toSphere(CubeFace face, float u, float v) {
        float cu = u * 2f - 1f;
        float cv = v * 2f - 1f;

        float x, y, z;
        switch (face) {
            case POS_X -> { x =  1f; y = cv; z = -cu; }
            case NEG_X -> { x = -1f; y = cv; z =  cu; }
            case POS_Y -> { x = cu;  y =  1f; z = -cv; }
            case NEG_Y -> { x = cu;  y = -1f; z =  cv; }
            case POS_Z -> { x = cu;  y = cv; z =  1f; }
            case NEG_Z -> { x = -cu; y = cv; z = -1f; }
            default -> throw new IllegalArgumentException();
        }

        return new Vector3(x, y, z).nor();
    }

    public static CubeFace dominantFace(Vector3 dir) {
        float ax = Math.abs(dir.x);
        float ay = Math.abs(dir.y);
        float az = Math.abs(dir.z);
        if (ax >= ay && ax >= az) return dir.x > 0 ? CubeFace.POS_X : CubeFace.NEG_X;
        if (ay >= ax && ay >= az) return dir.y > 0 ? CubeFace.POS_Y : CubeFace.NEG_Y;
        return dir.z > 0 ? CubeFace.POS_Z : CubeFace.NEG_Z;
    }

    public static float[] toFaceUV(Vector3 dir) {
        CubeFace face = dominantFace(dir);
        float cu, cv;
        switch (face) {
            case POS_X -> { cu = -dir.z / dir.x; cv = dir.y / dir.x; }
            case NEG_X -> { cu = dir.z / (-dir.x); cv = dir.y / (-dir.x); }
            case POS_Y -> { cu = dir.x / dir.y; cv = -dir.z / dir.y; }
            case NEG_Y -> { cu = dir.x / (-dir.y); cv = dir.z / (-dir.y); }
            case POS_Z -> { cu = dir.x / dir.z; cv = dir.y / dir.z; }
            case NEG_Z -> { cu = -dir.x / (-dir.z); cv = dir.y / (-dir.z); }
            default -> throw new IllegalArgumentException();
        }
        return new float[] { (cu + 1f) * 0.5f, (cv + 1f) * 0.5f };
    }

    public static float latitudeOf(Vector3 dir) {
        return (float) Math.asin(dir.y);
    }

    public static float longitudeOf(Vector3 dir) {
        return (float) Math.atan2(dir.z, dir.x);
    }
}
