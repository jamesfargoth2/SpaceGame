package com.galacticodyssey.ship.atmosphere;

/**
 * Physical properties of a planetary atmosphere used for aerodynamic and
 * thermal calculations. One instance per planet -- not an ECS component.
 */
public class AtmosphereProfile {

    public final float surfaceDensity;        // kg/m^3  (Earth ~ 1.225)
    public final float scaleHeight;           // metres  (Earth ~ 8500)
    public final float surfacePressure;       // Pa      (Earth ~ 101325)
    public final float adiabaticIndex;        // gamma   (diatomic ~ 1.4)
    public final float molarMass;             // kg/mol  (Earth air ~ 0.029)
    public final float surfaceTemperature;    // K       (Earth ~ 288)
    public final float atmosphereTopAltitude; // metres: above this density = 0

    private static final float UNIVERSAL_GAS_CONSTANT = 8.314f; // J/(mol*K)
    private static final float TROPOSPHERE_LAPSE_RATE = 0.0065f; // K/m
    private static final float TROPOSPHERE_CEILING = 11_000f;    // m

    public AtmosphereProfile(float surfaceDensity, float scaleHeight,
                             float surfacePressure, float adiabaticIndex,
                             float molarMass, float surfaceTemperature,
                             float atmosphereTopAltitude) {
        this.surfaceDensity = surfaceDensity;
        this.scaleHeight = scaleHeight;
        this.surfacePressure = surfacePressure;
        this.adiabaticIndex = adiabaticIndex;
        this.molarMass = molarMass;
        this.surfaceTemperature = surfaceTemperature;
        this.atmosphereTopAltitude = atmosphereTopAltitude;
    }

    /** Air density at the given altitude (metres above the surface). */
    public float densityAt(float altitude) {
        if (altitude >= atmosphereTopAltitude) return 0f;
        if (altitude < 0f) altitude = 0f;
        return surfaceDensity * (float) Math.exp(-altitude / scaleHeight);
    }

    /** Static pressure at the given altitude. */
    public float pressureAt(float altitude) {
        if (altitude >= atmosphereTopAltitude) return 0f;
        if (altitude < 0f) altitude = 0f;
        return surfacePressure * (float) Math.exp(-altitude / scaleHeight);
    }

    /**
     * Speed of sound at the given altitude, using a simple troposphere lapse
     * rate model. Above the troposphere ceiling the temperature is clamped.
     */
    public float speedOfSoundAt(float altitude) {
        float clampedAlt = Math.min(Math.max(altitude, 0f), TROPOSPHERE_CEILING);
        float temperature = surfaceTemperature - TROPOSPHERE_LAPSE_RATE * clampedAlt;
        if (temperature < 1f) temperature = 1f;
        return (float) Math.sqrt(adiabaticIndex * UNIVERSAL_GAS_CONSTANT / molarMass * temperature);
    }
}
