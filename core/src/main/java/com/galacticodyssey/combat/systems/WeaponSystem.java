package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.events.ReloadStartedEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;

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

    public WeaponSystem(EventBus eventBus) {
        super(Family.all(CombatInputComponent.class,
                         RangedWeaponComponent.class,
                         WeaponInventoryComponent.class).get(),
              PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = INPUT_M.get(entity);
        RangedWeaponComponent ranged = RANGED_M.get(entity);
        WeaponInventoryComponent inventory = INVENTORY_M.get(entity);

        // Skip if a weapon switch is in progress or the active slot is melee
        if (inventory.switching || inventory.isActiveSlotMelee()) {
            // Still consume fire requests so they don't linger
            input.fireRequested = false;
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
                if (input.fireHeld && ranged.fireTimer <= 0f) {
                    fireShot(entity, ranged, input);
                }
                // Do not consume fireRequested for AUTO (it's fireHeld-driven)
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
        eventBus.publish(new WeaponFiredEvent(entity, input.aimDirection, ranged.hitscan));
    }
}
