package com.galacticodyssey.planet;

import java.util.Map;

public final class AtmospherePhysics {
    private AtmospherePhysics() {}

    /** v_esc = 11.2 * sqrt(M/R) km/s (Earth units). */
    public static float escapeVelocity(float massEarth, float radiusEarth) {
        return 11.2f * (float) Math.sqrt(massEarth / radiusEarth);
    }

    /**
     * Jeans escape: gas retained if v_esc > 6 * v_thermal.
     * v_thermal = 0.157 * sqrt(T / molecularMass) km/s.
     */
    public static boolean gasRetained(float escVelKmS, float surfTempK, float molecularMass) {
        float vThermal = 0.157f * (float) Math.sqrt(surfTempK / molecularMass);
        return escVelKmS > 6f * vThermal;
    }

    /** Greenhouse multiplier from gas absorption. */
    public static float greenhouseMultiplier(Map<Gas, Float> composition, float pressure) {
        float effect = 0f;
        float co2 = composition.getOrDefault(Gas.CO2, 0f);
        float h2o = composition.getOrDefault(Gas.H2O, 0f);
        float ch4 = composition.getOrDefault(Gas.CH4, 0f);
        float so2 = composition.getOrDefault(Gas.SO2, 0f);
        effect += 0.7f * (float) Math.log1p(co2 * pressure * 10.0);
        effect += 1.0f * (float) Math.log1p(h2o * pressure * 8.0);
        effect += 1.5f * (float) Math.log1p(ch4 * pressure * 200.0);
        effect += 0.3f * (float) Math.log1p(so2 * pressure * 5.0);
        return Math.max(1.0f, 1.0f + effect);
    }

    /** Surface pressure from gravity * volatile inventory * outgassing. */
    public static float surfacePressure(float surfaceGravity, float volatileInventory, float ageGyr) {
        float outgassingFactor = 1.0f - (float) Math.exp(-ageGyr * 0.5);
        return surfaceGravity * volatileInventory * outgassingFactor;
    }
}
