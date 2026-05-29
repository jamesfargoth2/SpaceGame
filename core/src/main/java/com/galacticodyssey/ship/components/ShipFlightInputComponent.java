package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipFlightInputComponent implements Component {
    public float throttle;
    public float strafe;
    public float verticalThrust;
    public float pitchInput;
    public float yawInput;
    public float rollInput;
    public final boolean[] fireGroup = new boolean[4];
    public final boolean[] fireHeld = new boolean[4];
    public boolean targetLockPressed;
    public boolean nextTargetPressed;
    public boolean prevTargetPressed;
    public boolean cameraTogglePressed;
    public float scrollDelta;
    public boolean flightAssistTogglePressed;
    public boolean boostPressed;
}
