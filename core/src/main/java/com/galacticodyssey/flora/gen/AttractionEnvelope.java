package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;

import java.util.Random;

/** Generates a seeded cloud of attraction points filling a plant's growth envelope. */
public final class AttractionEnvelope {
    private AttractionEnvelope() {}

    public static Array<Vector3> generate(EnvelopeShape shape, float height, float radius,
                                          int count, Random rng) {
        Array<Vector3> pts = new Array<>(count);
        for (int i = 0; i < count; i++) {
            switch (shape) {
                case ELLIPSOID: pts.add(ellipsoid(height, radius, rng)); break;
                case CONE:      pts.add(cone(height, radius, rng)); break;
                case COLUMN:    pts.add(column(height, radius, rng)); break;
                case DOME:      pts.add(dome(height, radius, rng)); break;
                case CYLINDER:  pts.add(cylinder(height, radius, rng)); break;
            }
        }
        return pts;
    }

    /** Sphere centred at 0.6*height, vertical semi-axis 0.4*height, horizontal = radius. */
    private static Vector3 ellipsoid(float height, float radius, Random rng) {
        Vector3 u = inUnitSphere(rng);
        float cy = height * 0.6f, sy = height * 0.4f;
        return new Vector3(u.x * radius, cy + u.y * sy, u.z * radius);
    }

    /** Cone: widest at base of canopy (0.3*height), narrowing to apex at height. */
    private static Vector3 cone(float height, float radius, Random rng) {
        float y = lerp(height * 0.3f, height, rng.nextFloat());
        float frac = (y - height * 0.3f) / (height - height * 0.3f); // 0 at base .. 1 at apex
        float r = radius * (1f - frac) * (float) Math.sqrt(rng.nextFloat());
        return atAngle(r, y, rng);
    }

    /** Column (cactus): narrow vertical cylinder clustered around the axis. */
    private static Vector3 column(float height, float radius, Random rng) {
        float y = lerp(height * 0.15f, height, rng.nextFloat());
        float r = radius * 0.5f * (float) Math.sqrt(rng.nextFloat());
        return atAngle(r, y, rng);
    }

    /** Dome (shrub / lichen mound): hemisphere of the given radius sitting on the ground. */
    private static Vector3 dome(float height, float radius, Random rng) {
        Vector3 u = inUnitSphere(rng);
        float y = Math.abs(u.y) * height;
        return new Vector3(u.x * radius, y, u.z * radius);
    }

    private static Vector3 cylinder(float height, float radius, Random rng) {
        float y = rng.nextFloat() * height;
        float r = radius * (float) Math.sqrt(rng.nextFloat());
        return atAngle(r, y, rng);
    }

    private static Vector3 atAngle(float r, float y, Random rng) {
        double a = rng.nextFloat() * Math.PI * 2.0;
        return new Vector3(r * (float) Math.cos(a), y, r * (float) Math.sin(a));
    }

    /** Rejection-sampled point inside the unit sphere. */
    private static Vector3 inUnitSphere(Random rng) {
        float x, y, z;
        do {
            x = rng.nextFloat() * 2f - 1f;
            y = rng.nextFloat() * 2f - 1f;
            z = rng.nextFloat() * 2f - 1f;
        } while (x * x + y * y + z * z > 1f);
        return new Vector3(x, y, z);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
