package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Component;

/** Per-object thermal state for any entity participating in the planetside thermal sim. */
public class TemperatureComponent implements Component {
    public float temperature = 293f;   // K (current)
    public float thermalMass = 1000f;  // J/K (mass * specificHeat)
    public float surfaceArea = 1f;     // m^2 (for radiation/conduction)
    public String materialId;
    public ThermalMaterial material;   // resolved lazily by systems from materialId
    public ThermalState state = ThermalState.NORMAL;
    public float incomingHeat = 0f;    // W -- per-frame accumulator, reset by ObjectTemperatureSystem
}
