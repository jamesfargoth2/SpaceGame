package com.galacticodyssey.ship.power;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PowerStateSnapshot;

/**
 * Ship-wide power state. Tracks reactor output, battery/capacitor energy storage,
 * per-subsystem priority weights, base draws, and computed allocation ratios.
 *
 * <p>Power is measured in kilowatts (kW). Energy storage is in kilojoules (kJ).</p>
 */
public class PowerStateComponent implements Component, Snapshotable<PowerStateSnapshot> {

    // --- Reactor ---
    public float reactorBaseOutput = 100f;
    public float reactorEfficiency = 1.0f;
    public float reactorWasteHeatFactor = 0.15f;

    // --- Battery (slow charge, sustained backup) ---
    public float batteryCapacity = 500f;
    public float batteryCharge = 500f;
    public float batteryChargeRate = 50f;
    public float batteryDischargeRate = 80f;

    // --- Capacitor (fast charge, burst weapon energy) ---
    public float capacitorCapacity = 100f;
    public float capacitorCharge = 100f;
    public float capacitorChargeRate = 80f;

    // --- Priority weights (player-adjustable, 0-1) ---
    public float enginePriority = 1.0f;
    public float weaponPriority = 1.0f;
    public float shieldPriority = 1.0f;
    public float lifeSupportPriority = 1.0f;
    public float sensorPriority = 0.5f;

    // --- Subsystem base draws (kW, set from ship class data) ---
    public float engineMaxDraw = 50f;
    public float weaponMaxDraw = 20f;
    public float shieldMaxDraw = 15f;
    public float lifeSupportDraw = 5f;
    public float sensorMaxDraw = 10f;

    // --- Computed per frame by PowerSystem ---
    public float reactorCurrentOutput = 0f;
    public float totalDemand = 0f;
    public float totalSupply = 0f;

    // --- Allocation ratios (0-1, fraction of demand met) ---
    public float engineAllocation = 1f;
    public float weaponAllocation = 1f;
    public float shieldAllocation = 1f;
    public float lifeSupportAllocation = 1f;
    public float sensorAllocation = 1f;

    @Override
    public PowerStateSnapshot takeSnapshot() {
        PowerStateSnapshot s = new PowerStateSnapshot();
        s.reactorBaseOutput = reactorBaseOutput;
        s.reactorEfficiency = reactorEfficiency;
        s.reactorWasteHeatFactor = reactorWasteHeatFactor;
        s.batteryCapacity = batteryCapacity;
        s.batteryCharge = batteryCharge;
        s.batteryChargeRate = batteryChargeRate;
        s.batteryDischargeRate = batteryDischargeRate;
        s.capacitorCapacity = capacitorCapacity;
        s.capacitorCharge = capacitorCharge;
        s.capacitorChargeRate = capacitorChargeRate;
        s.enginePriority = enginePriority;
        s.weaponPriority = weaponPriority;
        s.shieldPriority = shieldPriority;
        s.lifeSupportPriority = lifeSupportPriority;
        s.sensorPriority = sensorPriority;
        s.engineMaxDraw = engineMaxDraw;
        s.weaponMaxDraw = weaponMaxDraw;
        s.shieldMaxDraw = shieldMaxDraw;
        s.lifeSupportDraw = lifeSupportDraw;
        s.sensorMaxDraw = sensorMaxDraw;
        return s;
    }

    @Override
    public void restoreFromSnapshot(PowerStateSnapshot s) {
        reactorBaseOutput = s.reactorBaseOutput;
        reactorEfficiency = s.reactorEfficiency;
        reactorWasteHeatFactor = s.reactorWasteHeatFactor;
        batteryCapacity = s.batteryCapacity;
        batteryCharge = s.batteryCharge;
        batteryChargeRate = s.batteryChargeRate;
        batteryDischargeRate = s.batteryDischargeRate;
        capacitorCapacity = s.capacitorCapacity;
        capacitorCharge = s.capacitorCharge;
        capacitorChargeRate = s.capacitorChargeRate;
        enginePriority = s.enginePriority;
        weaponPriority = s.weaponPriority;
        shieldPriority = s.shieldPriority;
        lifeSupportPriority = s.lifeSupportPriority;
        sensorPriority = s.sensorPriority;
        engineMaxDraw = s.engineMaxDraw;
        weaponMaxDraw = s.weaponMaxDraw;
        shieldMaxDraw = s.shieldMaxDraw;
        lifeSupportDraw = s.lifeSupportDraw;
        sensorMaxDraw = s.sensorMaxDraw;
    }
}
