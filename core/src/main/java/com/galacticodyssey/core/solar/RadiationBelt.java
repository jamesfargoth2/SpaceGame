package com.galacticodyssey.core.solar;

/**
 * Defines a radiation belt zone around a planet or magnetised body.
 * <p>
 * Dose rate follows a Gaussian (bell-curve) profile centred on
 * {@link #peakAltitude} with sigma = (outerRadius - innerRadius) * 0.25.
 * Returns zero outside the [innerRadius, outerRadius] range.
 */
public class RadiationBelt {

    /** Inner boundary radius from planet centre (metres). */
    public float innerRadius;

    /** Outer boundary radius from planet centre (metres). */
    public float outerRadius;

    /** Peak dose rate at {@link #peakAltitude} (arbitrary units / second). */
    public float peakDoseRate;

    /** Radius from planet centre where dose rate is highest (metres). */
    public float peakAltitude;

    public RadiationBelt() {
    }

    public RadiationBelt(float innerRadius, float outerRadius,
                         float peakDoseRate, float peakAltitude) {
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.peakDoseRate = peakDoseRate;
        this.peakAltitude = peakAltitude;
    }

    /**
     * Computes the dose rate at a given distance from the planet centre.
     *
     * @param radius distance from the planet centre (metres)
     * @return dose rate (units/s), or 0 if outside the belt boundaries
     */
    public float doseRateAt(float radius) {
        if (radius < innerRadius || radius > outerRadius) {
            return 0f;
        }
        float sigma = (outerRadius - innerRadius) * 0.25f;
        float x = radius - peakAltitude;
        return peakDoseRate * (float) Math.exp(-x * x / (2f * sigma * sigma));
    }
}
