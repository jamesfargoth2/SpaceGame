package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.events.ReloadStartedEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;

/**
 * Processes weapon firing and reloading each frame for entities that have
 * {@link CombatInputComponent}, {@link RangedWeaponComponent}, and
 * {@link WeaponInventoryComponent}.
 *
 * <p>Skips firing while the inventory is switching or while the active slot is melee.
 * Handles SEMI, AUTO, and BURST firing modes. Publishes {@link WeaponFiredEvent} on
 * each shot and {@link ReloadStartedEvent} when a reload is initiated.
 */
public class WeaponSystem extends IteratingSystem {

    public static final int PRIORITY = 4;

    private final EventBus eventBus;

    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<RangedWeaponComponent> RANGED_M =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<WeaponInventoryComponent> INVENTORY_M =
        ComponentMapper.getFor(WeaponInventoryComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<FPSCameraComponent> CAMERA_M =
        ComponentMapper.getFor(FPSCameraComponent.class);
    private static final ComponentMapper<com.galacticodyssey.player.components.PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(com.galacticodyssey.player.components.PlayerStateComponent.class);

    public WeaponSystem(EventBus eventBus) {
        super(Family.all(CombatInputComponent.class,
                         RangedWeaponComponent.class,
                         WeaponInventoryComponent.class).get(),
              PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        com.galacticodyssey.player.components.PlayerStateComponent state = STATE_M.get(entity);
        if (state != null
                && (state.currentMode == com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode.PILOTING
                 || state.currentMode == com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode.DRIVING)) {
            CombatInputComponent input = INPUT_M.get(entity);
            input.fireRequested = false;
            return;
        }
        CombatInputComponent input = INPUT_M.get(entity);
        RangedWeaponComponent ranged = RANGED_M.get(entity);
        WeaponInventoryComponent inventory = INVENTORY_M.get(entity);

        // Skip firing during weapon switch or melee, but still allow reload to start
        if (inventory.switching || inventory.isActiveSlotMelee()) {
            if (input.reloadRequested && !ranged.reloading) {
                ranged.reloading = true;
                ranged.reloadTimer = ranged.reloadTime;
                eventBus.publish(new ReloadStartedEvent(entity, ranged.reloadTime));
            }
            input.fireRequested = false;
            input.reloadRequested = false;
            return;
        }

        // --- Decrement fire cooldown timer ---
        if (ranged.fireTimer > 0f) {
            ranged.fireTimer -= deltaTime;
        }

        // --- Reload tick ---
        if (ranged.reloading) {
            ranged.reloadTimer -= deltaTime;
            if (ranged.reloadTimer <= 0f) {
                ranged.currentAmmo = ranged.magSize;
                ranged.reloading = false;
                ranged.reloadTimer = 0f;
            }
            // Cannot fire while reloading
            input.fireRequested = false;
            return;
        }

        // --- Initiate reload if requested or auto-reload on empty + fire attempt ---
        boolean wantsToFire = input.fireRequested || input.fireHeld;
        if (input.reloadRequested || (wantsToFire && ranged.currentAmmo <= 0)) {
            input.reloadRequested = false;
            if (!ranged.reloading) {
                ranged.reloading = true;
                ranged.reloadTimer = ranged.reloadTime;
                eventBus.publish(new ReloadStartedEvent(entity, ranged.reloadTime));
            }
            input.fireRequested = false;
            return;
        }

        // --- Cannot fire with empty mag ---
        if (ranged.currentAmmo <= 0) {
            input.fireRequested = false;
            return;
        }

        // --- Handle burst continuation ---
        if (ranged.firingMode == FiringMode.BURST && ranged.burstShotsRemaining > 0) {
            ranged.burstTimer -= deltaTime;
            if (ranged.burstTimer <= 0f && ranged.fireTimer <= 0f) {
                fireShot(entity, ranged, input);
                ranged.burstShotsRemaining--;
                if (ranged.burstShotsRemaining > 0) {
                    ranged.burstTimer = ranged.burstDelay;
                }
            }
            input.fireRequested = false;
            return;
        }

        // --- Firing modes ---
        FiringMode mode = ranged.firingMode != null ? ranged.firingMode : FiringMode.SEMI;

        switch (mode) {
            case SEMI:
                if (input.fireRequested && ranged.fireTimer <= 0f) {
                    fireShot(entity, ranged, input);
                    input.fireRequested = false;
                }
                break;

            case AUTO:
                if ((input.fireHeld || input.fireRequested) && ranged.fireTimer <= 0f) {
                    fireShot(entity, ranged, input);
                }
                input.fireRequested = false;
                break;

            case BURST:
                if (input.fireRequested && ranged.fireTimer <= 0f && ranged.burstShotsRemaining == 0) {
                    // Start a new burst: fire first shot immediately, queue the rest
                    fireShot(entity, ranged, input);
                    ranged.burstShotsRemaining = 2; // fire 2 more after this one
                    ranged.burstTimer = ranged.burstDelay;
                    input.fireRequested = false;
                }
                break;

            default:
                input.fireRequested = false;
                break;
        }
    }

    /** Fires one shot: decrements ammo, resets fire cooldown, publishes WeaponFiredEvent. */
    private void fireShot(Entity entity, RangedWeaponComponent ranged, CombatInputComponent input) {
        ranged.currentAmmo--;
        ranged.fireTimer = ranged.fireRate > 0f ? 1f / ranged.fireRate : 0f;
        eventBus.publish(new WeaponFiredEvent(entity, input.aimDirection, ranged.hitscan, computeMuzzlePosition(entity)));
    }

    /**
     * Ray origin for bullet/tracer spawning.
     * For first-person player entities: returns the camera eye position so that shots travel
     * exactly where the crosshair points.  For NPC entities (no FPSCameraComponent): falls back
     * to the transform position.
     */
    private Vector3 computeMuzzlePosition(Entity entity) {
        TransformComponent transform = TRANSFORM_M.get(entity);
        FPSCameraComponent cam = CAMERA_M.get(entity);
        if (transform == null) return new Vector3();

        if (cam != null) {
            // CameraSystem (priority 4, registered before WeaponSystem) has already written
            // worldEyePos this frame, so it is always current.
            if (cam.worldEyePos.len2() > 0f) {
                return new Vector3(cam.worldEyePos);
            }
            return new Vector3(transform.position).add(0, cam.currentEyeHeight, 0);
        }

        return new Vector3(transform.position);
    }
}
