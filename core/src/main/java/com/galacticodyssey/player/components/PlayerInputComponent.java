package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class PlayerInputComponent implements Component {
    public float moveForward;
    public float moveStrafe;
    public boolean sprint;
    public boolean jumpRequested;
    public boolean crouch;
    public float mouseDeltaX;
    public float mouseDeltaY;
    public boolean interactPressed;
    public boolean cameraTogglePressed;
    public boolean rollLeft;
    public boolean rollRight;
    public boolean thrustUp;
    public boolean thrustDown;
    public boolean leanLeft;
    public boolean leanRight;
    public float scrollDelta;
}
