package com.galacticodyssey.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;

/** Data-driven definition for a ground vehicle. Loaded from data/vehicles/*.json. */
public class VehicleDefinition {
    // Identity & rendering
    public String id;
    public String displayName;
    public String modelPath;
    public String sizeClass;

    // Physics (feeds GroundVehicleComponent + the rigid body)
    public float mass = 1200f;
    public float wheelbase = 3f;
    public float trackWidth = 2f;
    public float groundClearance = 0.4f;
    public float maxDriveForce = 8000f;
    public float maxSteerAngle = 35f;
    public float anchorBreakForce = 50000f;
    public float dynamicLift = 0f;

    // Survivability
    public float maxHP = 200f;
    public float armorValue = 0f;

    // Bay footprint
    public int baySlots = 1;

    // Inline weapon (populates a RangedWeaponComponent)
    public VehicleWeaponStats weapon;

    public static class VehicleWeaponStats {
        public float damage = 20f;
        public float fireRate = 3f;
        public float range = 120f;
        public boolean hitscan = true;
        public float projectileSpeed = 200f;
        public DamageType damageType = DamageType.BALLISTIC;
        public FiringMode firingMode = FiringMode.AUTO;
        public int magSize = 50;
        public float reloadTime = 2.5f;
        public float spread = 1.5f;
    }
}
