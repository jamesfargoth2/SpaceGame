package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.MeleeWeaponComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.data.WeaponStatsResolver;
import com.galacticodyssey.combat.data.WeaponStatsResolver.MeleeStats;
import com.galacticodyssey.combat.data.WeaponStatsResolver.RangedStats;
import com.galacticodyssey.combat.events.WeaponSwitchedEvent;
import com.galacticodyssey.core.EventBus;

/**
 * Handles weapon switching for entities with {@link CombatInputComponent} and
 * {@link WeaponInventoryComponent}.
 *
 * <p>When a switch is requested to a different slot that contains a weapon, the system
 * sets the inventory into a switching state and counts down a timer whose duration is
 * determined by {@link WeaponSlot#switchTime} of the <em>target</em> slot. On completion,
 * the active slot index is updated, the weapon stats are resolved and applied to the
 * appropriate component ({@link RangedWeaponComponent} or {@link MeleeWeaponComponent}),
 * and a {@link WeaponSwitchedEvent} is published.
 *
 * <p>A switch request also cancels any active reload on the current ranged weapon.
 */
public class WeaponSwitchSystem extends IteratingSystem {

    public static final int PRIORITY = 3;

    private final EventBus eventBus;
    private final WeaponDataRegistry weaponData;

    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<WeaponInventoryComponent> INVENTORY_M =
        ComponentMapper.getFor(WeaponInventoryComponent.class);
    private static final ComponentMapper<RangedWeaponComponent> RANGED_M =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<MeleeWeaponComponent> MELEE_M =
        ComponentMapper.getFor(MeleeWeaponComponent.class);

    public WeaponSwitchSystem(EventBus eventBus, WeaponDataRegistry weaponData) {
        super(Family.all(CombatInputComponent.class,
                         WeaponInventoryComponent.class).get(),
              PRIORITY);
        this.eventBus = eventBus;
        this.weaponData = weaponData;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = INPUT_M.get(entity);
        WeaponInventoryComponent inventory = INVENTORY_M.get(entity);

        // --- Continue an in-progress switch ---
        if (inventory.switching) {
            inventory.switchTimer -= deltaTime;
            if (inventory.switchTimer <= 0f) {
                completeSwitchFor(entity, inventory);
            }
            return;
        }

        // --- Check for a new switch request ---
        int requested = input.switchSlotRequested;
        if (requested < 0) return;
        input.switchSlotRequested = -1; // consume

        // Ignore if requesting the already-active slot
        if (requested == inventory.activeSlotIndex) return;

        // Validate slot index and ensure it holds a weapon
        if (requested < 0 || requested >= inventory.slots.length) return;
        WeaponAssembly targetAssembly = inventory.slots[requested];
        if (targetAssembly == null) return;

        // Cancel any active reload
        RangedWeaponComponent ranged = RANGED_M.get(entity);
        if (ranged != null && ranged.reloading) {
            ranged.reloading = false;
            ranged.reloadTimer = 0f;
        }

        // Begin switching
        WeaponSlot targetSlot = WeaponSlot.values()[requested];
        inventory.pendingSlotIndex = requested;
        inventory.switching = true;
        inventory.switchTimer = targetSlot.switchTime;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void completeSwitchFor(Entity entity, WeaponInventoryComponent inventory) {
        WeaponSlot oldSlot = inventory.getActiveSlot();

        inventory.activeSlotIndex = inventory.pendingSlotIndex;
        inventory.switching = false;
        inventory.switchTimer = 0f;

        WeaponSlot newSlot = inventory.getActiveSlot();

        resolveActiveWeapon(entity, inventory);

        eventBus.publish(new WeaponSwitchedEvent(entity, oldSlot, newSlot));
    }

    /**
     * Resolves the active weapon assembly into stats and writes them onto the
     * {@link RangedWeaponComponent} or {@link MeleeWeaponComponent} of the entity.
     */
    private void resolveActiveWeapon(Entity entity, WeaponInventoryComponent inventory) {
        WeaponAssembly assembly = inventory.getActiveAssembly();
        if (assembly == null) return;

        if (assembly.isMelee) {
            MeleeWeaponComponent meleeComp = MELEE_M.get(entity);
            if (meleeComp == null) return;

            MeleeStats stats = WeaponStatsResolver.resolveMelee(assembly, weaponData);
            meleeComp.baseDamage      = stats.baseDamage;
            meleeComp.swingSpeed      = stats.speed;
            meleeComp.reach           = stats.reach;
            meleeComp.blockEfficiency = stats.blockEfficiency;
            meleeComp.damageType      = stats.damageType;
            meleeComp.weightClass     = stats.weightClass;
            if (stats.directionalModifiers != null) {
                for (AttackDirection dir : AttackDirection.values()) {
                    Float mod = stats.directionalModifiers.get(dir.name());
                    if (mod != null) {
                        meleeComp.directionalModifiers.put(dir, mod);
                    }
                }
            }
        } else {
            RangedWeaponComponent rangedComp = RANGED_M.get(entity);
            if (rangedComp == null) return;

            RangedStats stats = WeaponStatsResolver.resolveRanged(assembly, weaponData);
            rangedComp.damage              = stats.damage;
            rangedComp.fireRate            = stats.fireRate;
            rangedComp.spread              = stats.spread;
            rangedComp.range               = stats.range;
            rangedComp.recoil              = stats.recoil;
            rangedComp.magSize             = stats.magSize;
            rangedComp.reloadTime          = stats.reloadTime;
            rangedComp.firingMode          = stats.firingMode;
            rangedComp.hitscan             = stats.hitscan;
            rangedComp.damageType          = stats.damageType;
            rangedComp.statusEffect        = stats.statusEffect;
            rangedComp.statusEffectChance  = stats.statusEffectChance;
            rangedComp.projectileSpeed     = stats.projectileSpeed;
            rangedComp.ammoTypeId          = stats.ammoTypeId;
            rangedComp.grenadeTypeId       = stats.grenadeTypeId;
            // Reset timers, ammo, and heat for the newly drawn weapon
            rangedComp.currentAmmo         = stats.magSize;
            rangedComp.fireTimer           = 0f;
            rangedComp.reloading           = false;
            rangedComp.reloadTimer         = 0f;
            rangedComp.currentHeatSpread   = 0f;
        }
    }
}
