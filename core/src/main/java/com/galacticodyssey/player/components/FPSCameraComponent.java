package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

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

    public final Vector3 localUp = new Vector3(0, 1, 0);

    public float targetCameraDistance;
    public float currentCameraDistance;
    public float maxCameraDistance = 12f;
    public float zoomStep = 1.5f;
    public float zoomLerpSpeed = 10f;
}
