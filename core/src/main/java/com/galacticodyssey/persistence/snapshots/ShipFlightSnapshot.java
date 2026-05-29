package com.galacticodyssey.persistence.snapshots;

public class ShipFlightSnapshot {
    public float linearThrust;
    public float strafeThrustFraction;
    public float verticalThrustFraction;
    public float pitchYawTorque;
    public float rollTorque;
    public float linearDrag;
    public float angularDrag;
    public float currentThrottle;
    public float reverseFraction;
    public float faLinearGain;
    public float faLateralBleed;
    public float blueZoneLow;
    public float blueZoneHigh;
    public float offBandTurnScale;
    public float rotStiffness;
    public float boostSpeedMultiplier;
    public float boostForce;
    public float boostDuration;
    public float boostEnergyCost;
    public float boostMaxEnergy;
    public float boostRechargeRate;
    public float boostCooldown;
    public boolean flightAssistEnabled = true;
    public float boostEnergy;
    public float boostTimer;
    public float boostCooldownTimer;
    public ShipFlightSnapshot() {}
}
