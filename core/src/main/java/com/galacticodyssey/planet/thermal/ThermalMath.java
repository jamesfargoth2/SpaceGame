package com.galacticodyssey.planet.thermal;

/** Stateless thermal physics helpers shared by the planetside thermal systems. */
public final class ThermalMath {
    private ThermalMath() {}

    /** Stefan-Boltzmann constant (W / m^2 K^4). */
    public static final float STEFAN_BOLTZMANN = 5.67e-8f;

    /** Linear conduction/convection coefficient toward ambient (W / m^2 K). */
    public static final float CONDUCTION_COEFF = 5f;

    /** Net radiative power leaving the surface (W). Negative when ambient is hotter. */
    public static float radiativeCooling(float surfaceTemp, float ambientTemp,
                                         float area, float emissivity) {
        float t4 = surfaceTemp * surfaceTemp * surfaceTemp * surfaceTemp;
        float ta4 = ambientTemp * ambientTemp * ambientTemp * ambientTemp;
        return emissivity * STEFAN_BOLTZMANN * area * (t4 - ta4);
    }

    /** Net conductive power leaving the surface toward ambient (W). Negative when ambient is hotter. */
    public static float conduction(float surfaceTemp, float ambientTemp, float area) {
        return CONDUCTION_COEFF * area * (surfaceTemp - ambientTemp);
    }
}
