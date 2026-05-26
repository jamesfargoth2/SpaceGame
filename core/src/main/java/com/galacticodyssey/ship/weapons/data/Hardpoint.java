package com.galacticodyssey.ship.weapons.data;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;

public class Hardpoint {
    public final String id;
    public final Vector3 position = new Vector3();
    public final HardpointType type;
    public final HardpointSize sizeClass;
    public float arcMin;
    public float arcMax;
    public ShipWeaponData mountedWeapon;
    public HardpointState currentState = HardpointState.IDLE;
    public final Quaternion currentRotation = new Quaternion();
    public float fireTimer;

    public Hardpoint(String id, HardpointType type, HardpointSize sizeClass, float arcMin, float arcMax) {
        this.id = id;
        this.type = type;
        this.sizeClass = sizeClass;
        this.arcMin = arcMin;
        this.arcMax = arcMax;
    }

    public boolean isInArc(float angle) {
        if (arcMax >= 360f) return true;
        float half = arcMax / 2f;
        return angle >= -half && angle <= half;
    }

    public boolean isEmpty() { return mountedWeapon == null; }
}
