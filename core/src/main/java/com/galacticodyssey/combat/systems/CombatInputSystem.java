package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;

/**
 * Translates raw input flags and camera angles into {@link CombatInputComponent} data
 * each frame.
 *
 * <p>Runs at priority 1 so that downstream combat systems (WeaponSystem, MeleeSystem,
 * HitscanSystem) always see up-to-date aim data in the same tick.</p>
 *
 * <p>One-shot inputs (fire, reload, melee, quickMelee, switchSlot) are cleared
 * after they are transferred, so each press produces exactly one event.</p>
 */
public class CombatInputSystem extends IteratingSystem {

    public static final int PRIORITY = 1;

    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<FPSCameraComponent> CAMERA_M =
        ComponentMapper.getFor(FPSCameraComponent.class);

    // --- pending input state set by the input handler ---

    private boolean pendingFire;
    private boolean pendingFireHeld;
    private boolean pendingReload;
    private boolean pendingMeleeAttack;
    private boolean pendingBlock;
    private boolean pendingBlockHeld;
    private boolean pendingQuickMelee;
    private int pendingSwitchSlot = -1;
    private boolean pendingGrenadeThrow;
    private boolean pendingGrenadeThrowHeld;

    /** Mouse-delta accumulated since the last frame, used to resolve melee direction. */
    private float mouseDeltaX;
    private float mouseDeltaY;

    public CombatInputSystem() {
        super(Family.all(CombatInputComponent.class,
                         FPSCameraComponent.class,
                         PlayerTagComponent.class).get(),
              PRIORITY);
    }

    // -------------------------------------------------------------------------
    // Public setters — called by the input handler before engine.update()
    // -------------------------------------------------------------------------

    /** One-shot: next update will set {@code fireRequested = true}. */
    public void setFireInput(boolean fire) {
        pendingFire = fire;
    }

    /** Held: component's {@code fireHeld} mirrors this value every frame. */
    public void setFireHeldInput(boolean held) {
        pendingFireHeld = held;
    }

    /** One-shot reload request. */
    public void setReloadInput() {
        pendingReload = true;
    }

    /** One-shot or held melee attack request. */
    public void setMeleeAttackInput(boolean attack) {
        pendingMeleeAttack = attack;
    }

    /** One-shot block request. */
    public void setBlockInput(boolean block) {
        pendingBlock = block;
    }

    /** Held block request. */
    public void setBlockHeldInput(boolean held) {
        pendingBlockHeld = held;
    }

    /** One-shot quick-melee (e.g. dedicated key / bumper). */
    public void setQuickMeleeInput() {
        pendingQuickMelee = true;
    }

    /** One-shot grenade throw request (default keybind: G). */
    public void setGrenadeThrowInput(boolean throwGrenade) {
        if (throwGrenade) pendingGrenadeThrow = true;
    }

    /** Held: component's {@code grenadeThrowHeld} mirrors this value every frame. */
    public void setGrenadeThrowHeldInput(boolean held) {
        pendingGrenadeThrowHeld = held;
    }

    /**
     * One-shot slot-switch request.
     *
     * @param slot target slot index; pass {@code -1} to cancel any pending switch
     */
    public void setSwitchSlotInput(int slot) {
        pendingSwitchSlot = slot;
    }

    /**
     * Provide the raw mouse delta for this frame so the system can resolve the
     * directional intent of a melee swing.
     *
     * @param dx horizontal mouse movement (positive = right)
     * @param dy vertical mouse movement (positive = down in screen-space)
     */
    public void setMouseDeltaForMelee(float dx, float dy) {
        mouseDeltaX = dx;
        mouseDeltaY = dy;
    }

    // -------------------------------------------------------------------------
    // IteratingSystem
    // -------------------------------------------------------------------------

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = INPUT_M.get(entity);
        FPSCameraComponent camera = CAMERA_M.get(entity);

        // 1. Derive aim direction from the effective (recoil-applied) camera angles so that
        //    bullets always travel along the rendered crosshair direction.
        float yawRad   = camera.effectiveYawAngle   * MathUtils.degreesToRadians;
        float pitchRad = camera.effectivePitchAngle * MathUtils.degreesToRadians;

        float cosPitch = MathUtils.cos(pitchRad);
        input.aimDirection.set(
            -MathUtils.sin(yawRad) * cosPitch,   // x  (matches camera forward; yaw decreases on right-turn)
             MathUtils.sin(pitchRad),             // y  (positive pitch looks up)
            -MathUtils.cos(yawRad) * cosPitch    // z  (negative = forward)
        );

        // 2. Transfer one-shot and held input flags.
        input.fireRequested  = pendingFire;
        input.fireHeld       = pendingFireHeld;
        input.reloadRequested = pendingReload;
        input.meleeAttackRequested = pendingMeleeAttack;
        input.blockRequested = pendingBlock;
        input.blockHeld      = pendingBlockHeld;
        input.aimHeld        = pendingBlockHeld;
        input.quickMeleeRequested = pendingQuickMelee;
        input.switchSlotRequested = pendingSwitchSlot;
        input.grenadeThrowRequested = pendingGrenadeThrow;
        input.grenadeThrowHeld      = pendingGrenadeThrowHeld;

        // 3. Resolve melee direction from mouse delta (only meaningful when attacking).
        if (pendingMeleeAttack) {
            input.meleeAttackDirection = resolveMeleeDirection(mouseDeltaX, mouseDeltaY);
        }

        // 4. Clear one-shot inputs so they fire for exactly one tick.
        pendingFire          = false;
        pendingReload        = false;
        pendingQuickMelee    = false;
        pendingSwitchSlot    = -1;
        pendingGrenadeThrow  = false;
        mouseDeltaX          = 0f;
        mouseDeltaY          = 0f;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a mouse-delta vector into one of the four {@link AttackDirection} values.
     *
     * <ul>
     *   <li>Dominant horizontal motion → LEFT or RIGHT</li>
     *   <li>Dominant upward motion    → OVERHEAD</li>
     *   <li>Dominant downward motion  → THRUST</li>
     * </ul>
     *
     * If both deltas are zero the method falls back to THRUST (a safe default).
     */
    private static AttackDirection resolveMeleeDirection(float dx, float dy) {
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);

        if (absDx >= absDy) {
            // Horizontal dominant — resolve left / right.
            return dx >= 0f ? AttackDirection.RIGHT : AttackDirection.LEFT;
        } else {
            // Vertical dominant — screen-Y increases downward, so negative dy = mouse up.
            return dy <= 0f ? AttackDirection.OVERHEAD : AttackDirection.THRUST;
        }
    }
}
