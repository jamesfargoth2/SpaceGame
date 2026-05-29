package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipFlightSnapshot;

public class ShipFlightComponent implements Component, Snapshotable<ShipFlightSnapshot> {
    public float linearThrust;
    public float strafeThrustFraction;
    public float verticalThrustFraction;
    public float pitchYawTorque;
    public float rollTorque;
    public float linearDrag;
    public float angularDrag;
    public float currentThrottle;
    public float timeDilationFactor = 1f;
    public float gravitationalTimeDilation = 1f;

    // --- ED flight tuning (data-driven) ---
    public float reverseFraction = 0.4f;
    public float faLinearGain = 1.5f;      // 1/s, forward-speed P-gain (mass-relative)
    public float faLateralBleed = 1.2f;    // 1/s, drift-cancel gain (mass-relative)
    public float blueZoneLow = 0.4f;
    public float blueZoneHigh = 0.8f;
    public float offBandTurnScale = 0.5f;
    public float rotStiffness = 4f;        // rate-error → torque-fraction stiffness
    public float boostSpeedMultiplier = 1.6f;
    public float boostForce = 40000f;      // extra forward N during boost
    public float boostDuration = 5f;       // s
    public float boostEnergyCost = 50f;
    public float boostMaxEnergy = 100f;
    public float boostRechargeRate = 12f;  // energy/s when idle
    public float boostCooldown = 3f;       // s

    // --- ED flight runtime state (persisted) ---
    public boolean flightAssistEnabled = true;
    public float boostEnergy = 100f;
    public float boostTimer = 0f;
    public float boostCooldownTimer = 0f;

    @Override
    public ShipFlightSnapshot takeSnapshot() {
        ShipFlightSnapshot s = new ShipFlightSnapshot();
        s.linearThrust = linearThrust;
        s.strafeThrustFraction = strafeThrustFraction;
        s.verticalThrustFraction = verticalThrustFraction;
        s.pitchYawTorque = pitchYawTorque;
        s.rollTorque = rollTorque;
        s.linearDrag = linearDrag;
        s.angularDrag = angularDrag;
        s.currentThrottle = currentThrottle;
        s.reverseFraction = reverseFraction;
        s.faLinearGain = faLinearGain;
        s.faLateralBleed = faLateralBleed;
        s.blueZoneLow = blueZoneLow;
        s.blueZoneHigh = blueZoneHigh;
        s.offBandTurnScale = offBandTurnScale;
        s.rotStiffness = rotStiffness;
        s.boostSpeedMultiplier = boostSpeedMultiplier;
        s.boostForce = boostForce;
        s.boostDuration = boostDuration;
        s.boostEnergyCost = boostEnergyCost;
        s.boostMaxEnergy = boostMaxEnergy;
        s.boostRechargeRate = boostRechargeRate;
        s.boostCooldown = boostCooldown;
        s.flightAssistEnabled = flightAssistEnabled;
        s.boostEnergy = boostEnergy;
        s.boostTimer = boostTimer;
        s.boostCooldownTimer = boostCooldownTimer;
        return s;
    }

    @Override
    public void restoreFromSnapshot(ShipFlightSnapshot s) {
        linearThrust = s.linearThrust;
        strafeThrustFraction = s.strafeThrustFraction;
        verticalThrustFraction = s.verticalThrustFraction;
        pitchYawTorque = s.pitchYawTorque;
        rollTorque = s.rollTorque;
        linearDrag = s.linearDrag;
        angularDrag = s.angularDrag;
        currentThrottle = s.currentThrottle;
        reverseFraction = s.reverseFraction;
        faLinearGain = s.faLinearGain;
        faLateralBleed = s.faLateralBleed;
        blueZoneLow = s.blueZoneLow;
        blueZoneHigh = s.blueZoneHigh;
        offBandTurnScale = s.offBandTurnScale;
        rotStiffness = s.rotStiffness;
        boostSpeedMultiplier = s.boostSpeedMultiplier;
        boostForce = s.boostForce;
        boostDuration = s.boostDuration;
        boostEnergyCost = s.boostEnergyCost;
        boostMaxEnergy = s.boostMaxEnergy;
        boostRechargeRate = s.boostRechargeRate;
        boostCooldown = s.boostCooldown;
        flightAssistEnabled = s.flightAssistEnabled;
        boostEnergy = s.boostEnergy;
        boostTimer = s.boostTimer;
        boostCooldownTimer = s.boostCooldownTimer;
    }
}
