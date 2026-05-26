package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipFlightComponent implements Component {
    public float linearThrust;
    public float strafeThrustFraction;
    public float verticalThrustFraction;
    public float pitchYawTorque;
    public float rollTorque;
    public float linearDrag;
    public float angularDrag;
    public float currentThrottle;
}
