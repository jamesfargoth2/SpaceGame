package com.galacticodyssey.ship.lifesupport;

import com.badlogic.gdx.math.MathUtils;

/**
 * Utility class for pressurisation and depressurisation of compartments.
 * Handles airlock cycling, hull breaches, and supply-line pressurisation.
 *
 * <p>Not an Ashley system -- call these methods directly from other systems
 * or game logic that manages airlocks and breaches.
 */
public final class PressureCycleSystem {

    /** Critical pressure ratio for choked flow (air, gamma ~1.4). */
    private static final float CRITICAL_RATIO = 0.528f;

    private PressureCycleSystem() {
        // Utility class
    }

    /**
     * Pressurise a compartment from a supply line. Flow is proportional to the
     * pressure difference between supply and compartment. The supplied gas mixture
     * is Earth-like (78% N2, 21% O2, 1% other absorbed into N2).
     *
     * @param atmo           the compartment atmosphere
     * @param supplyPressure supply line pressure (kPa)
     * @param valveFlowRate  valve flow coefficient (kPa/s per kPa of pressure difference)
     * @param dt             time step (seconds)
     */
    public static void pressurise(CompartmentAtmosphereComponent atmo,
                                  float supplyPressure, float valveFlowRate, float dt) {
        if (atmo.totalPressure >= supplyPressure) return;

        float pressureDiff = supplyPressure - atmo.totalPressure;
        float flowRate = valveFlowRate * pressureDiff;
        float pressureAdded = flowRate * dt;

        // Clamp so we don't overshoot supply pressure
        pressureAdded = Math.min(pressureAdded, supplyPressure - atmo.totalPressure);

        atmo.n2Pressure += pressureAdded * 0.78f;
        atmo.o2Pressure += pressureAdded * 0.21f;
        // Remaining 1% absorbed into N2 for simplicity
        atmo.n2Pressure += pressureAdded * 0.01f;
        atmo.totalPressure = atmo.o2Pressure + atmo.co2Pressure + atmo.n2Pressure;
    }

    /**
     * Vent a compartment to external pressure (vacuum or another compartment).
     * Uses choked or subsonic flow depending on the pressure ratio across the vent.
     *
     * @param atmo             the compartment atmosphere
     * @param ventArea         effective vent area (m2)
     * @param externalPressure external pressure (kPa), 0 for vacuum
     * @param dt               time step (seconds)
     */
    public static void vent(CompartmentAtmosphereComponent atmo,
                            float ventArea, float externalPressure, float dt) {
        if (atmo.totalPressure <= externalPressure || atmo.totalPressure <= 0f) return;

        float pressureRatio = externalPressure / atmo.totalPressure;

        // Flow factor: choked (sonic) if pressure ratio <= critical, else subsonic
        float flowFactor;
        if (pressureRatio <= CRITICAL_RATIO) {
            flowFactor = 1f;
        } else {
            flowFactor = (float) Math.sqrt(Math.max(0f, 1f - pressureRatio));
        }

        // Mass flow rate approximation (simplified compressible flow)
        float massFlowRate = 0.6f * ventArea * atmo.totalPressure * 1000f
                             * flowFactor / (float) Math.sqrt(atmo.temperature);

        // Fraction of atmosphere lost this timestep
        float fraction = massFlowRate * dt / (atmo.totalPressure * atmo.volume);
        fraction = MathUtils.clamp(fraction, 0f, 1f);

        // All gases vent proportionally
        atmo.o2Pressure *= (1f - fraction);
        atmo.co2Pressure *= (1f - fraction);
        atmo.n2Pressure *= (1f - fraction);
        atmo.totalPressure = atmo.o2Pressure + atmo.co2Pressure + atmo.n2Pressure;
    }
}
