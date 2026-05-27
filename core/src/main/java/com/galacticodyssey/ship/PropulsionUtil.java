package com.galacticodyssey.ship;

public final class PropulsionUtil {

    public static final float G0 = 9.80665f;

    private PropulsionUtil() {}

    public static float deltaVBudget(float isp, float wetMass, float dryMass) {
        if (dryMass <= 0f || wetMass <= dryMass) return 0f;
        return isp * G0 * (float) Math.log(wetMass / dryMass);
    }

    public static float remainingDeltaV(float isp, float currentFuelMass, float shipDryMass) {
        float wetMass = shipDryMass + currentFuelMass;
        return deltaVBudget(isp, wetMass, shipDryMass);
    }

    public static float massFlowRate(float thrust, float isp) {
        if (isp <= 0f) return 0f;
        return thrust / (isp * G0);
    }

    public static float burnTime(float fuelMass, float thrust, float isp) {
        float rate = massFlowRate(thrust, isp);
        if (rate <= 0f) return 0f;
        return fuelMass / rate;
    }
}
