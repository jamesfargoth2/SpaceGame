package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;

/**
 * A cubic Bezier curve defining a ship's spine (center line) from nose to tail.
 * <p>
 * Parameterized by four control points p0..p3, where p0 is the nose and p3 is
 * the tail. Used by ShipHullGenerator to distribute cross-sections along the hull.
 */
public class SpineCurve {

    private final Vector3 p0, p1, p2, p3;

    /**
     * Constructs a SpineCurve from four cubic Bezier control points.
     * The control points are copied, so the caller may reuse them freely.
     *
     * @param p0 start (nose) control point
     * @param p1 first interior control point
     * @param p2 second interior control point
     * @param p3 end (tail) control point
     */
    public SpineCurve(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3) {
        this.p0 = new Vector3(p0);
        this.p1 = new Vector3(p1);
        this.p2 = new Vector3(p2);
        this.p3 = new Vector3(p3);
    }

    /**
     * Evaluates the cubic Bezier at parameter {@code t}.
     * <p>
     * B(t) = (1-t)³p0 + 3(1-t)²t·p1 + 3(1-t)t²·p2 + t³·p3
     *
     * @param t curve parameter in [0, 1]
     * @return point on the curve (new Vector3 instance)
     */
    public Vector3 evaluate(float t) {
        float u  = 1f - t;
        float u2 = u * u;
        float u3 = u2 * u;
        float t2 = t * t;
        float t3 = t2 * t;

        return new Vector3(
            u3 * p0.x + 3f * u2 * t * p1.x + 3f * u * t2 * p2.x + t3 * p3.x,
            u3 * p0.y + 3f * u2 * t * p1.y + 3f * u * t2 * p2.y + t3 * p3.y,
            u3 * p0.z + 3f * u2 * t * p1.z + 3f * u * t2 * p2.z + t3 * p3.z
        );
    }

    /**
     * Returns the normalized first derivative (tangent direction) at parameter {@code t}.
     * <p>
     * B'(t) = 3(1-t)²(p1-p0) + 6(1-t)t(p2-p1) + 3t²(p3-p2)
     *
     * @param t curve parameter in [0, 1]
     * @return unit-length tangent vector (new Vector3 instance)
     */
    public Vector3 tangent(float t) {
        float u  = 1f - t;
        float u2 = u * u;
        float t2 = t * t;

        return new Vector3(
            3f * u2 * (p1.x - p0.x) + 6f * u * t * (p2.x - p1.x) + 3f * t2 * (p3.x - p2.x),
            3f * u2 * (p1.y - p0.y) + 6f * u * t * (p2.y - p1.y) + 3f * t2 * (p3.y - p2.y),
            3f * u2 * (p1.z - p0.z) + 6f * u * t * (p2.z - p1.z) + 3f * t2 * (p3.z - p2.z)
        ).nor();
    }

    /**
     * Approximates the arc length of the curve by summing chord lengths between
     * {@code segments} evenly spaced samples.
     *
     * @param segments number of subdivisions (higher = more accurate)
     * @return approximate arc length in the same units as the control points
     */
    public float approximateLength(int segments) {
        float length = 0f;
        Vector3 prev = evaluate(0f);
        for (int i = 1; i <= segments; i++) {
            Vector3 curr = evaluate((float) i / segments);
            length += prev.dst(curr);
            prev = curr;
        }
        return length;
    }

    /** @return the start (nose) control point; do not mutate. */
    public Vector3 getP0() { return p0; }

    /** @return the end (tail) control point; do not mutate. */
    public Vector3 getP3() { return p3; }
}
