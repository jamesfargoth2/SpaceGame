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
    }
}
