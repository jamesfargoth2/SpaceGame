package com.galacticodyssey.ship.lifesupport;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.CompartmentAtmosphereSnapshot;

/**
 * Tracks the atmospheric composition and conditions of a pressurised compartment.
 * Partial pressures are stored in kPa, volume in m3, temperature in Kelvin.
 */
public class CompartmentAtmosphereComponent implements Component, Pool.Poolable, Snapshotable<CompartmentAtmosphereSnapshot> {

    // --- Partial pressures (kPa) ---
    public float o2Pressure = 21.0f;
    public float co2Pressure = 0.04f;
    public float n2Pressure = 79.0f;
    public float totalPressure = 100.04f;

    // --- Compartment properties ---
    public float volume = 50.0f;        // m3
    public float temperature = 293.0f;  // K (~20 C)

    // --- Crew ---
    public int crewCount = 0;
    public float activityLevel = 1.0f;  // multiplier (RESTING=1.0)

    // --- Health severity accumulators ---
    public float hypoxiaSeverity = 0f;
    public float co2ToxicitySeverity = 0f;
    public float decompressionSeverity = 0f;

    /** Fraction of O2 in the atmosphere (dimensionless, 0-1). */
    public float o2Fraction() {
        return totalPressure > 0f ? o2Pressure / totalPressure : 0f;
    }

    /** Fraction of CO2 in the atmosphere (dimensionless, 0-1). */
    public float co2Fraction() {
        return totalPressure > 0f ? co2Pressure / totalPressure : 0f;
    }

    /**
     * Returns the mass (kg) of a gas in the compartment using the ideal gas law:
     * m = P * V * M / (R * T), where P is in Pa (partialPressure * 1000).
     *
     * @param partialPressure partial pressure in kPa
     * @param molarMass       molar mass in kg/mol (e.g. 0.032 for O2)
     * @return mass in kg
     */
    public float gasMassKg(float partialPressure, float molarMass) {
        return (partialPressure * 1000f) * volume * molarMass
               / (8.314f * temperature);
    }

    @Override
    public void reset() {
        o2Pressure = 21.0f;
        co2Pressure = 0.04f;
        n2Pressure = 79.0f;
        totalPressure = 100.04f;
        volume = 50.0f;
        temperature = 293.0f;
        crewCount = 0;
        activityLevel = 1.0f;
        hypoxiaSeverity = 0f;
        co2ToxicitySeverity = 0f;
        decompressionSeverity = 0f;
    }

    @Override
    public CompartmentAtmosphereSnapshot takeSnapshot() {
        CompartmentAtmosphereSnapshot s = new CompartmentAtmosphereSnapshot();
        s.o2Pressure = o2Pressure;
        s.co2Pressure = co2Pressure;
        s.n2Pressure = n2Pressure;
        s.volume = volume;
        s.temperature = temperature;
        s.crewCount = crewCount;
        s.activityLevel = activityLevel;
        return s;
    }

    @Override
    public void restoreFromSnapshot(CompartmentAtmosphereSnapshot s) {
        o2Pressure = s.o2Pressure;
        co2Pressure = s.co2Pressure;
        n2Pressure = s.n2Pressure;
        volume = s.volume;
        temperature = s.temperature;
        crewCount = s.crewCount;
        activityLevel = s.activityLevel;
        totalPressure = o2Pressure + co2Pressure + n2Pressure;
    }
}
