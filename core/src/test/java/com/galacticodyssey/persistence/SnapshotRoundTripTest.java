package com.galacticodyssey.persistence;

import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.persistence.snapshots.FPSCameraSnapshot;
import com.galacticodyssey.persistence.snapshots.HealthSnapshot;
import com.galacticodyssey.persistence.snapshots.MovementStateSnapshot;
import com.galacticodyssey.persistence.snapshots.PlayerStateSnapshot;
import com.galacticodyssey.persistence.snapshots.PlayerWalletSnapshot;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotRoundTripTest {

    // -------------------------------------------------------------------------
    // HealthComponent
    // -------------------------------------------------------------------------

    @Test
    void health_roundTripPreservesAllFields() {
        HealthComponent comp = new HealthComponent();
        comp.currentHP = 42.5f;
        comp.maxHP = 200f;
        comp.alive = false;

        HealthSnapshot snap = comp.takeSnapshot();

        HealthComponent restored = new HealthComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(42.5f, restored.currentHP, 1e-5f);
        assertEquals(200f, restored.maxHP, 1e-5f);
        assertFalse(restored.alive);
    }

    @Test
    void health_defaultValuesRoundTrip() {
        HealthComponent comp = new HealthComponent();

        HealthSnapshot snap = comp.takeSnapshot();
        HealthComponent restored = new HealthComponent();
        restored.currentHP = 0f;
        restored.maxHP = 0f;
        restored.alive = false;
        restored.restoreFromSnapshot(snap);

        assertEquals(100f, restored.currentHP, 1e-5f);
        assertEquals(100f, restored.maxHP, 1e-5f);
        assertTrue(restored.alive);
    }

    // -------------------------------------------------------------------------
    // PlayerStateComponent
    // -------------------------------------------------------------------------

    @Test
    void playerState_roundTripPreservesMode() {
        PlayerStateComponent comp = new PlayerStateComponent();
        comp.currentMode = PlayerMode.PILOTING;
        comp.currentShipId = null;
        comp.interactionTargetId = null;

        PlayerStateSnapshot snap = comp.takeSnapshot();

        PlayerStateComponent restored = new PlayerStateComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(PlayerMode.PILOTING, restored.currentMode);
        assertNull(restored.currentShipId);
        assertNull(restored.interactionTargetId);
        // Entity references must not be touched by restore (resolved by ReferenceResolver later)
        assertNull(restored.currentShip);
        assertNull(restored.interactionTarget);
    }

    @Test
    void playerState_roundTripPreservesUUIDs() {
        UUID shipId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        PlayerStateComponent comp = new PlayerStateComponent();
        comp.currentMode = PlayerMode.ON_FOOT_INTERIOR;
        comp.currentShipId = shipId;
        comp.interactionTargetId = targetId;

        PlayerStateSnapshot snap = comp.takeSnapshot();

        PlayerStateComponent restored = new PlayerStateComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(PlayerMode.ON_FOOT_INTERIOR, restored.currentMode);
        assertEquals(shipId, restored.currentShipId);
        assertEquals(targetId, restored.interactionTargetId);
    }

    @Test
    void playerState_defaultModeIsOnFootExterior() {
        PlayerStateComponent comp = new PlayerStateComponent();

        PlayerStateSnapshot snap = comp.takeSnapshot();
        PlayerStateComponent restored = new PlayerStateComponent();
        restored.currentMode = PlayerMode.PILOTING;
        restored.restoreFromSnapshot(snap);

        assertEquals(PlayerMode.ON_FOOT_EXTERIOR, restored.currentMode);
    }

    // -------------------------------------------------------------------------
    // MovementStateComponent
    // -------------------------------------------------------------------------

    @Test
    void movement_roundTripPreservesAllFields() {
        MovementStateComponent comp = new MovementStateComponent();
        comp.isGrounded = true;
        comp.isSprinting = true;
        comp.isCrouching = false;
        comp.currentSpeed = 7.3f;
        comp.currentStamina = 55f;
        comp.maxStamina = 120f;
        comp.staminaDrainRate = 25f;
        comp.staminaRegenRate = 8f;
        comp.slopeAngle = 15f;
        comp.isExhausted = true;
        comp.fallVelocity = -12f;

        MovementStateSnapshot snap = comp.takeSnapshot();

        MovementStateComponent restored = new MovementStateComponent();
        restored.restoreFromSnapshot(snap);

        assertTrue(restored.isGrounded);
        assertTrue(restored.isSprinting);
        assertFalse(restored.isCrouching);
        assertEquals(7.3f, restored.currentSpeed, 1e-5f);
        assertEquals(55f, restored.currentStamina, 1e-5f);
        assertEquals(120f, restored.maxStamina, 1e-5f);
        assertEquals(25f, restored.staminaDrainRate, 1e-5f);
        assertEquals(8f, restored.staminaRegenRate, 1e-5f);
        assertEquals(15f, restored.slopeAngle, 1e-5f);
        assertTrue(restored.isExhausted);
        assertEquals(-12f, restored.fallVelocity, 1e-5f);
    }

    @Test
    void movement_groundNormalNotAffectedByRestore() {
        MovementStateComponent comp = new MovementStateComponent();
        comp.isGrounded = true;
        comp.currentStamina = 80f;

        MovementStateSnapshot snap = comp.takeSnapshot();

        MovementStateComponent restored = new MovementStateComponent();
        // groundNormal starts as (0,1,0) — restore must not corrupt it
        restored.restoreFromSnapshot(snap);

        assertEquals(0f, restored.groundNormal.x, 1e-6f);
        assertEquals(1f, restored.groundNormal.y, 1e-6f);
        assertEquals(0f, restored.groundNormal.z, 1e-6f);
    }

    // -------------------------------------------------------------------------
    // FPSCameraComponent
    // -------------------------------------------------------------------------

    @Test
    void fpsCamera_roundTripPreservesSnapshotFields() {
        FPSCameraComponent comp = new FPSCameraComponent();
        comp.pitchAngle = -30f;
        comp.yawAngle = 180f;
        comp.currentEyeHeight = 1.0f;
        comp.mouseSensitivity = 0.25f;
        comp.currentCameraDistance = 5f;
        comp.maxCameraDistance = 20f;

        FPSCameraSnapshot snap = comp.takeSnapshot();

        FPSCameraComponent restored = new FPSCameraComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(-30f, restored.pitchAngle, 1e-5f);
        assertEquals(180f, restored.yawAngle, 1e-5f);
        assertEquals(1.0f, restored.currentEyeHeight, 1e-5f);
        assertEquals(0.25f, restored.mouseSensitivity, 1e-5f);
        assertEquals(5f, restored.currentCameraDistance, 1e-5f);
        assertEquals(20f, restored.maxCameraDistance, 1e-5f);
    }

    @Test
    void fpsCamera_nonSnapshotFieldsUntouchedByRestore() {
        FPSCameraComponent comp = new FPSCameraComponent();
        comp.pitchAngle = 10f;
        comp.headBobAmplitude = 0.08f;  // not in snapshot

        FPSCameraSnapshot snap = comp.takeSnapshot();

        FPSCameraComponent restored = new FPSCameraComponent();
        restored.headBobAmplitude = 0.04f;  // default
        restored.restoreFromSnapshot(snap);

        // headBobAmplitude must be unchanged by restore
        assertEquals(0.04f, restored.headBobAmplitude, 1e-5f);
    }

    // -------------------------------------------------------------------------
    // PlayerWalletComponent
    // -------------------------------------------------------------------------

    @Test
    void wallet_roundTripPreservesCredits() {
        PlayerWalletComponent comp = new PlayerWalletComponent();
        comp.credits = 9_999_999L;

        PlayerWalletSnapshot snap = comp.takeSnapshot();

        PlayerWalletComponent restored = new PlayerWalletComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(9_999_999L, restored.credits);
    }

    @Test
    void wallet_zeroCreditsRoundTrip() {
        PlayerWalletComponent comp = new PlayerWalletComponent();
        comp.credits = 0L;

        PlayerWalletSnapshot snap = comp.takeSnapshot();

        PlayerWalletComponent restored = new PlayerWalletComponent();
        restored.credits = 500L;
        restored.restoreFromSnapshot(snap);

        assertEquals(0L, restored.credits);
    }

    @Test
    void wallet_negativeCreditsRoundTrip() {
        PlayerWalletComponent comp = new PlayerWalletComponent();
        comp.credits = -100L;

        PlayerWalletSnapshot snap = comp.takeSnapshot();

        PlayerWalletComponent restored = new PlayerWalletComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(-100L, restored.credits);
    }

    // -------------------------------------------------------------------------
    // Snapshot independence — modifying snapshot after takeSnapshot must not
    // affect the original component.
    // -------------------------------------------------------------------------

    @Test
    void health_snapshotIsIndependentOfComponent() {
        HealthComponent comp = new HealthComponent();
        comp.currentHP = 80f;

        HealthSnapshot snap = comp.takeSnapshot();
        snap.currentHP = 0f;   // mutate snapshot

        // Original component must be unaffected
        assertEquals(80f, comp.currentHP, 1e-5f);
    }

    @Test
    void wallet_snapshotIsIndependentOfComponent() {
        PlayerWalletComponent comp = new PlayerWalletComponent();
        comp.credits = 500L;

        PlayerWalletSnapshot snap = comp.takeSnapshot();
        snap.credits = 0L;   // mutate snapshot

        assertEquals(500L, comp.credits);
    }
}
