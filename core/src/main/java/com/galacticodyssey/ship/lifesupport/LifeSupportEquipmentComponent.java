package com.galacticodyssey.ship.lifesupport;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Tracks the state of life support equipment in a compartment:
 * CO2 scrubber and O2 generator / reserve tank.
 */
public class LifeSupportEquipmentComponent implements Component, Pool.Poolable {

    // --- CO2 scrubber ---
    public float scrubberCapacity = 5.0f;    // kg of CO2 absorbable before saturation
    public float scrubberAbsorbed = 0f;      // kg currently absorbed
    public float scrubRate = 0.005f;         // kg/s absorption rate
    public boolean scrubberOperational = true;

    // --- O2 generator / reserve ---
    public float reserveTankKg = 50.0f;      // kg of O2 stored in reserve
    public float generationRate = 0.001f;    // kg/s (electrolysis or chemical)
    public boolean generatorOnline = true;

    @Override
    public void reset() {
        scrubberCapacity = 5.0f;
        scrubberAbsorbed = 0f;
        scrubRate = 0.005f;
        scrubberOperational = true;
        reserveTankKg = 50.0f;
        generationRate = 0.001f;
        generatorOnline = true;
    }
}
