package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class FPSCameraComponent implements Component {
    public float eyeHeight = 1.7f;
    public float crouchEyeHeight = 1.0f;
    public float currentEyeHeight = 1.7f;
    public float headBobAmplitude = 0.04f;
    public float headBobFrequency = 8.0f;
    public float headBobPhase;
    public float pitchAngle;
    public float yawAngle;
    public float mouseSensitivity = 0.15f;
    public float landingDipAmount;
}
