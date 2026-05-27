package com.galacticodyssey.core.solar;

import com.badlogic.ashley.core.Component;

/**
 * Added to ships or objects that are affected by solar radiation pressure,
 * solar wind, and radiation belt exposure.
 */
public class SolarAffectedComponent implements Component {

    /** Projected cross-section area facing the star (m^2). */
    public float projectedArea;

    /** Surface reflectivity (0 = fully absorptive, 1 = fully reflective). */
    public float reflectivity;

    /**
     * Magnetic shielding factor (0 = no shielding, 1 = full shielding).
     * Reduces solar wind force and radiation dose.
     */
    public float magneticShieldFactor;

    /** Accumulated radiation dose received over time. */
    public float accumulatedRadiationDose;
}
