package com.galacticodyssey.ship.weapons;

public final class ShipWeaponEnums {
    public enum HardpointType { TURRET, FIXED, BROADSIDE, MISSILE_BAY, POINT_DEFENSE }
    public enum HardpointSize { SMALL, MEDIUM, LARGE, CAPITAL }
    public enum HardpointState { IDLE, TRACKING, FIRING, RELOADING, DISABLED }
    public enum ShipWeaponCategory { BALLISTIC_CANNON, LASER_ARRAY, PLASMA_TURRET, MISSILE_LAUNCHER, RAILGUN, EMP_PROJECTOR, POINT_DEFENSE, FLAK_CANNON }
    private ShipWeaponEnums() {}
}
