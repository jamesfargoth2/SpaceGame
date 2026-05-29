package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FPSCameraSnapshot;

public class FPSCameraComponent implements Component, Snapshotable<FPSCameraSnapshot> {
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

    public float leanAngle;
    public float maxLeanAngle = 15f;
    public float leanSpeed = 8f;
    public float leanBodyShift = 0.25f;       // how far the body slides sideways at full lean (m); must be < leanHorizontalOffset
    public float leanHorizontalOffset = 0.4f; // total camera-eye offset from capsule centre at full lean (m)

    public final Vector3 localUp = new Vector3(0, 1, 0);

    public float baseFov = 75f;

    /** Camera world-space position after all offsets (eye height, head bob, lean, landing dip).
     *  Written by CameraSystem each frame; read by WeaponSystem for accurate muzzle placement. */
    public final Vector3 worldEyePos = new Vector3();

    /** World-space position of the gun barrel tip, written by GameScreen after the FP weapon
     *  transform is built each render frame. Used by WeaponSystem next frame for muzzle placement. */
    public final Vector3 worldBarrelTip = new Vector3();

    public float targetCameraDistance;
    public float currentCameraDistance;
    public float maxCameraDistance = 12f;
    public float zoomStep = 1.5f;
    public float zoomLerpSpeed = 10f;

    @Override
    public FPSCameraSnapshot takeSnapshot() {
        FPSCameraSnapshot s = new FPSCameraSnapshot();
        s.pitchAngle = pitchAngle;
        s.yawAngle = yawAngle;
        s.currentEyeHeight = currentEyeHeight;
        s.mouseSensitivity = mouseSensitivity;
        s.currentCameraDistance = currentCameraDistance;
        s.maxCameraDistance = maxCameraDistance;
        return s;
    }

    @Override
    public void restoreFromSnapshot(FPSCameraSnapshot s) {
        pitchAngle = s.pitchAngle;
        yawAngle = s.yawAngle;
        currentEyeHeight = s.currentEyeHeight;
        mouseSensitivity = s.mouseSensitivity;
        currentCameraDistance = s.currentCameraDistance;
        maxCameraDistance = s.maxCameraDistance;
    }
}
