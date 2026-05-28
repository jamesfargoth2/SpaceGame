package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Component;

/** Present while an entity is on fire. Added by CombustionSystem on IgnitionEvent. */
public class BurningComponent implements Component {
    public float intensity = 1f;
    public float fuelRemaining = 0f; // J -- initialized from material.combustionEnergy
    public float heatOutput = 0f;    // W -- copied from material.burnHeatOutput
    public boolean statusApplied = false; // BURNING status applied once
}
