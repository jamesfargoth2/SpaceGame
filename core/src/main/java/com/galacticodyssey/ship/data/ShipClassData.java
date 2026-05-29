package com.galacticodyssey.ship.data;

/** Immutable data record describing a ship class loaded from JSON. */
public class ShipClassData {
    public String id;
    public String name;
    /** SMALL, MEDIUM, or LARGE */
    public String sizeClass;
    public float mass;
    public float linearThrust;
    public float strafeThrustFraction;
    public float verticalThrustFraction;
    public float pitchYawTorque;
    public float rollTorque;
    public float linearDrag;
    public float angularDrag;
    public float maxIsp;
    public float maxThrust;
    public float throttleResponseRate;
    public float fuelCapacity;
    public float wingArea;
    public float dragCoefficient;
    public float crossSectionArea;
    public float stallAngle;
    public float maxLiftCoefficient;
    public float controlSurfaceAuthority;
    public float vtolThrustFraction;
    /** Lift coefficient sampled at evenly-spaced AoA steps. */
    public float[] liftCurve;
    /** Hardpoint IDs per weapon group (comma-separated within each entry). */
    public String[] defaultWeaponGroups;

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
}
