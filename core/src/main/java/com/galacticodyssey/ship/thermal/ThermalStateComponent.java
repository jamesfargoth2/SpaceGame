package com.galacticodyssey.ship.thermal;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ThermalStateSnapshot;

/**
 * Ship-wide thermal state tracking. Stores per-subsystem temperatures, thermal
 * masses, operating limits, radiator geometry, and heat sink capacity.
 *
 * <p>All temperatures are in Kelvin. Thermal masses are in J/K. Heat sink
 * capacity is in Joules. Areas are in m&sup2;.</p>
 *
 * <p>This component covers the ship as a whole; weapon-specific per-hardpoint
 * heat is handled separately by {@code ShipWeaponHeatComponent}.</p>
 */
public class ThermalStateComponent implements Component, Snapshotable<ThermalStateSnapshot> {

    // --- Per-subsystem temperatures (Kelvin) ---
    public float engineTemp = 300f;
    public float weaponBankTemp = 300f;
    public float hullTemp = 300f;
    public float reactorTemp = 300f;

    // --- Heat sink ---
    /** Heat sink charge level, 0 (empty) to 1 (fully saturated). */
    public float heatSinkCharge = 0f;
    /** Maximum energy the heat sinks can absorb (Joules). */
    public float heatSinkCapacity = 500_000f;

    // --- Thermal masses (J/K) ---
    public float engineThermalMass = 50_000f;
    public float hullThermalMass = 200_000f;
    public float weaponThermalMass = 30_000f;

    // --- Operating limits (Kelvin) ---
    public float engineMaxSafeTemp = 1800f;
    public float engineDamageTemp = 2500f;
    public float weaponMaxSafeTemp = 1200f;
    public float hullMaxSafeTemp = 1500f;

    // --- Radiator properties ---
    /** Total exposed radiator surface area in m^2. */
    public float radiatorArea = 20f;
    /** Radiator emissivity (0-1). Engineered radiators ~0.9. */
    public float radiatorEmissivity = 0.9f;

    // --- Hull surface properties ---
    /** Total hull surface area in m^2. */
    public float hullArea = 80f;
    /** Fraction of incident radiation absorbed by the hull (0-1). */
    public float hullAbsorptivity = 0.3f;

    // --- Environment ---
    /** Background ambient temperature in Kelvin. Deep space ~3 K. */
    public float ambientTemp = 3f;

    // --- Engine heat generation ---
    /** Maximum engine heat output rate in Watts at full throttle. */
    public float engineMaxHeatRate = 500_000f;

    // --- Heat sink passive drain ---
    /** Fraction of heat sink charge passively dissipated per second. */
    public float heatSinkPassiveDrainRate = 0.02f;

    // --- Computed penalty fields (written by ThermalPenaltySystem) ---
    /** Engine throttle cap (0-1). 1.0 = no restriction. */
    public float throttleCap = 1f;
    /** Weapon fire rate multiplier (0-1). 1.0 = no restriction. */
    public float fireRateMultiplier = 1f;

    @Override
    public ThermalStateSnapshot takeSnapshot() {
        ThermalStateSnapshot s = new ThermalStateSnapshot();
        s.engineTemp = engineTemp;
        s.weaponsTemp = weaponBankTemp;
        s.hullTemp = hullTemp;
        s.reactorTemp = reactorTemp;
        s.heatSinkCharge = heatSinkCharge;
        s.heatSinkCapacity = heatSinkCapacity;
        s.radiatorArea = radiatorArea;
        s.radiatorEfficiency = radiatorEmissivity;
        s.engineTempLimit = engineMaxSafeTemp;
        s.weaponsTempLimit = weaponMaxSafeTemp;
        s.hullTempLimit = hullMaxSafeTemp;
        s.reactorTempLimit = engineDamageTemp; // no dedicated reactor limit; proxy with engineDamageTemp
        return s;
    }

    @Override
    public void restoreFromSnapshot(ThermalStateSnapshot s) {
        engineTemp = s.engineTemp;
        weaponBankTemp = s.weaponsTemp;
        hullTemp = s.hullTemp;
        reactorTemp = s.reactorTemp;
        heatSinkCharge = s.heatSinkCharge;
        heatSinkCapacity = s.heatSinkCapacity;
        radiatorArea = s.radiatorArea;
        radiatorEmissivity = s.radiatorEfficiency;
        engineMaxSafeTemp = s.engineTempLimit;
        weaponMaxSafeTemp = s.weaponsTempLimit;
        hullMaxSafeTemp = s.hullTempLimit;
        engineDamageTemp = s.reactorTempLimit;
    }
}
