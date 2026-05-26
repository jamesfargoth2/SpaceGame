package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class MovementStateComponent implements Component {
    public boolean isGrounded;
    public boolean isSprinting;
    public boolean isCrouching;
    public float currentSpeed;
    public float currentStamina = 100f;
    public float maxStamina = 100f;
    public float staminaDrainRate = 20f;
    public float staminaRegenRate = 10f;
    public final Vector3 groundNormal = new Vector3(0, 1, 0);
    public float slopeAngle;
    public boolean isExhausted;
    public float fallVelocity;
}
