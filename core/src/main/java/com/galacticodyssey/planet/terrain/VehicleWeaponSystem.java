package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Thin firing trigger for vehicle weapons. Advances the fire-rate timer, decrements ammo,
 * handles a simple reload, and publishes {@link WeaponFiredEvent} so the existing
 * HitscanSystem/ProjectileSystem/DamageSystem pipeline handles the shot. SEMI + AUTO only.
 */
public class VehicleWeaponSystem extends IteratingSystem {

    public static final int PRIORITY = 4;

    private final EventBus eventBus;

    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<RangedWeaponComponent> WEAPON_M =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    public VehicleWeaponSystem(EventBus eventBus) {
        super(Family.all(VehicleTagComponent.class, CombatInputComponent.class,
                         RangedWeaponComponent.class, TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = INPUT_M.get(entity);
        RangedWeaponComponent w = WEAPON_M.get(entity);

        if (w.fireTimer > 0f) w.fireTimer -= deltaTime;

        if (w.reloading) {
            w.reloadTimer -= deltaTime;
            if (w.reloadTimer <= 0f) {
                w.currentAmmo = w.magSize;
                w.reloading = false;
                w.reloadTimer = 0f;
            }
            input.fireRequested = false;
            return;
        }

        boolean wantsFire = input.fireRequested || input.fireHeld;
        if (!wantsFire) { input.fireRequested = false; return; }

        if (w.currentAmmo <= 0) {
            if (w.reloadTime > 0f) {
                w.reloading = true;
                w.reloadTimer = w.reloadTime;
            }
            input.fireRequested = false;
            return;
        }

        FiringMode mode = w.firingMode != null ? w.firingMode : FiringMode.AUTO;
        boolean trigger = (mode == FiringMode.SEMI) ? input.fireRequested : wantsFire;
        if (trigger && w.fireTimer <= 0f) {
            fire(entity, w, input);
        }
        input.fireRequested = false;
    }

    private void fire(Entity entity, RangedWeaponComponent w, CombatInputComponent input) {
        w.currentAmmo--;
        w.fireTimer = w.fireRate > 0f ? 1f / w.fireRate : 0f;
        Vector3 muzzle = new Vector3(TRANSFORM_M.get(entity).position);
        eventBus.publish(new WeaponFiredEvent(entity, input.aimDirection, w.hitscan, muzzle));
    }
}
