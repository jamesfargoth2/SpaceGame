package com.galacticodyssey.ship.atmosphere;

/**
 * Utility for computing terminal velocity -- the speed at which aerodynamic
 * drag exactly balances gravitational pull.
 */
public final class TerminalVelocityUtil {

    private TerminalVelocityUtil() { }

    /**
     * Terminal velocity for a body falling through atmosphere.
     *
     * @param mass    body mass in kg
     * @param g       gravitational acceleration in m/s^2
     * @param density air density in kg/m^3
     * @param cd      drag coefficient (dimensionless)
     * @param area    reference cross-section area in m^2
     * @return terminal velocity in m/s, or 0 if inputs prevent a valid result
     */
    public static float terminalVelocity(float mass, float g, float density,
                                         float cd, float area) {
        float denominator = density * cd * area;
        if (denominator <= 0f) return 0f;
        return (float) Math.sqrt(2f * mass * g / denominator);
    }
}
