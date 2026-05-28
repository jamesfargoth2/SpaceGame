package com.galacticodyssey.common.protocol;

public class PlayerInput {
    public int sequenceNumber;

    // FPS inputs
    public float moveForward;
    public float moveStrafe;
    public float mouseDeltaX;
    public float mouseDeltaY;
    public boolean jump;
    public boolean sprint;
    public boolean crouch;
    public boolean fire;
    public boolean ads;

    // Ship inputs
    public float throttle;
    public float pitchInput;
    public float yawInput;
    public float rollInput;
    public float strafe;
    public float verticalThrust;
    public boolean[] fireGroup;

    // Mode
    public int playerMode;

    public PlayerInput() {
        fireGroup = new boolean[4];
    }
}
