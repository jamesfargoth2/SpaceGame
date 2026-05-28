package com.galacticodyssey.planet.thermal;

/** Data-driven thermal/combustion properties for a material. Loaded from data/thermal/materials.json. */
public class ThermalMaterial {
    public String id;
    public String name;
    public float specificHeat;        // J/(kg*K)
    public float emissivity = 0.85f;  // 0..1, radiative coefficient
    public float ignitionPoint;       // K -- at/above this (and enough O2), ignites
    public float freezePoint;         // K -- at/below this, freezes
    public boolean flammable;
    public boolean freezable;
    public float flammability;        // 0..1, weights ignition ease & wildfire spread
    public float combustionEnergy;    // J -- total fuel energy released over a full burn
    public float burnHeatOutput;      // W emitted while burning at intensity 1.0
    public boolean consumedWhenBurnt; // true -> object destroyed when fuel exhausts
    public String charMaterialId;     // material it becomes after burning (nullable)
    public float frozenSpeedMultiplier = 1f; // movement multiplier other systems read when frozen
    public boolean brittleWhenFrozen; // shatters on impact when frozen
}
