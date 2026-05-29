package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.MovementStateSnapshot;

public class MovementStateComponent implements Component, Snapshotable<MovementStateSnapshot> {
    public boolean isGrounded;
    public boolean isSprinting;
    public boolean isCrouching;
    public boolean isProne;
    public float currentSpeed;
    public float currentStamina = 100f;
    public float maxStamina = 100f;
    public float staminaDrainRate = 20f;
    public float staminaRegenRate = 10f;
    public final Vector3 groundNormal = new Vector3(0, 1, 0);
    public float slopeAngle;
    public boolean isExhausted;
    public float fallVelocity;

    @Override
    public MovementStateSnapshot takeSnapshot() {
        MovementStateSnapshot s = new MovementStateSnapshot();
        s.isGrounded = isGrounded;
        s.isSprinting = isSprinting;
        s.isCrouching = isCrouching;
        s.isProne = isProne;
        s.currentSpeed = currentSpeed;
        s.currentStamina = currentStamina;
        s.maxStamina = maxStamina;
        s.staminaDrainRate = staminaDrainRate;
        s.staminaRegenRate = staminaRegenRate;
        s.slopeAngle = slopeAngle;
        s.isExhausted = isExhausted;
        s.fallVelocity = fallVelocity;
        return s;
    }

    @Override
    public void restoreFromSnapshot(MovementStateSnapshot s) {
        isGrounded = s.isGrounded;
        isSprinting = s.isSprinting;
        isCrouching = s.isCrouching;
        isProne = s.isProne;
        currentSpeed = s.currentSpeed;
        currentStamina = s.currentStamina;
        maxStamina = s.maxStamina;
        staminaDrainRate = s.staminaDrainRate;
        staminaRegenRate = s.staminaRegenRate;
        slopeAngle = s.slopeAngle;
        isExhausted = s.isExhausted;
        fallVelocity = s.fallVelocity;
    }
}
