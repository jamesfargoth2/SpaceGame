package com.galacticodyssey.mech.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Core physics state for a mech entity. Updated each tick by orientation,
 * locomotion, and ground-contact systems.
 */
public class MechPhysicsComponent implements Component {

    /** Locomotion mode determines which movement model is active. */
    public enum LocomotionMode {
        WALKING,
        JETPACK
    }

    // --- Linear state ---
    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public float mass;
    public float maxWalkSpeed;
    public float acceleration;
    public float deceleration;

    // --- Angular state (torso heading) ---
    public float yaw;
    public float yawVelocity;
    public float maxYawRate;
    public float yawDamping;

    // --- Orientation ---
    public final Vector3 gravityDir = new Vector3(0f, -1f, 0f);
    public final Quaternion bodyOrientation = new Quaternion();

    // --- Ground state ---
    public boolean isGrounded;
    public final Vector3 groundNormal = new Vector3(0f, 1f, 0f);
    public float groundClearance;
    public float stepHeight;

    // --- Armor / material properties ---
    public float dragCoefficient = 0.8f;
    public float restitution = 0.05f;
    public float frictionCoeff = 0.6f;
    public float impactDamageFactor;

    // --- Mode ---
    public LocomotionMode locomotionMode = LocomotionMode.WALKING;
}
