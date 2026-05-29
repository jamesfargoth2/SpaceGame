package com.galacticodyssey.persistence.snapshots;

public class MovementStateSnapshot {
    public boolean isGrounded;
    public boolean isSprinting;
    public boolean isCrouching;
    public int crouchHeightStep = 2;
    public float currentSpeed;
    public float currentStamina;
    public float maxStamina;
    public float staminaDrainRate;
    public float staminaRegenRate;
    public float slopeAngle;
    public boolean isExhausted;
    public float fallVelocity;
    public MovementStateSnapshot() {}
}
