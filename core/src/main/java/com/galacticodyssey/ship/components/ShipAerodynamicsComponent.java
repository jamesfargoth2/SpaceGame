package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipAerodynamicsComponent implements Component {
    public float wingArea;
    public float dragCoefficient;
    public float maxLiftCoefficient;
    public float stallAngle;
    public float controlSurfaceAuthority;
    public float vtolThrustFraction;
    public float crossSectionArea;
    public float heatShieldRating;
    public float[] liftCurve = new float[10];

    public float getLiftCoefficient(float aoaDegrees) {
        float clamped = Math.max(0, Math.min(90, aoaDegrees));
        float index = clamped / 10f;
        int lo = (int) index;
        int hi = Math.min(lo + 1, liftCurve.length - 1);
        float frac = index - lo;
        return liftCurve[lo] * (1 - frac) + liftCurve[hi] * frac;
    }
}
