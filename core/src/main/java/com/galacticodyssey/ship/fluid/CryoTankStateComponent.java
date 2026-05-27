package com.galacticodyssey.ship.fluid;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * State of a cryogenic propellant tank (liquid hydrogen, liquid oxygen, etc.).
 * Tracks vapour mass, pressure, and boil-off parameters. Must be paired with
 * {@link SloshTankComponent} on the same entity.
 */
public class CryoTankStateComponent implements Component, Pool.Poolable {

    /** Mass of vapour in the ullage space (kg). */
    public float vaporMass = 0f;

    /** Molar mass of the propellant (kg/mol). e.g. 0.002 for H2, 0.032 for O2. */
    public float molarMass = 0.002f;

    /** Temperature of the ullage vapour (K). */
    public float temperature = 20f;

    /** Volume of the ullage space above the liquid (m^3). */
    public float vaporVolume = 0.1f;

    /** Current vapour pressure (Pa). Computed from ideal gas law. */
    public float pressure = 0f;

    /** Maximum allowable pressure before the relief valve opens (Pa). */
    public float maxPressure = 300000f;

    /** Energy required to vapourise one kg of liquid (J/kg). */
    public float vapourisationEnthalpy = 446000f;

    @Override
    public void reset() {
        vaporMass = 0f;
        molarMass = 0.002f;
        temperature = 20f;
        vaporVolume = 0.1f;
        pressure = 0f;
        maxPressure = 300000f;
        vapourisationEnthalpy = 446000f;
    }
}
