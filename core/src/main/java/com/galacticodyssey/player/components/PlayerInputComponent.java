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
}
