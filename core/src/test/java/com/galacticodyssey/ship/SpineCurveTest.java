package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpineCurveTest {

    @Test
    void evaluateReturnsStartPointAtT0() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 result = curve.evaluate(0f);

        assertEquals(0f, result.x, 0.001f);
        assertEquals(0f, result.y, 0.001f);
        assertEquals(0f, result.z, 0.001f);
    }

    @Test
    void evaluateReturnsEndPointAtT1() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 1, -3);
        Vector3 p2 = new Vector3(0, 1, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 result = curve.evaluate(1f);

        assertEquals(0f, result.x, 0.001f);
        assertEquals(0f, result.y, 0.001f);
        assertEquals(-10f, result.z, 0.001f);
    }

    @Test
    void evaluateAtMidpointIsBetweenEndpoints() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 result = curve.evaluate(0.5f);

        assertTrue(result.z < 0f && result.z > -10f,
            "Midpoint z should be between 0 and -10, was " + result.z);
    }

    @Test
    void tangentAtStartPointsAlongCurve() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 tangent = curve.tangent(0f);

        assertTrue(tangent.z < 0f, "Tangent at t=0 should point in -Z direction");
        assertEquals(0f, tangent.x, 0.001f);
    }

    @Test
    void spineLength() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        float length = curve.approximateLength(32);

        assertEquals(10f, length, 0.5f);
    }
}
