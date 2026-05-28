package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.ActiveStatusEffect;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.ShieldComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.data.AmmoTypeData;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.data.StatusEffectData;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.ShieldAbsorbEvent;
import com.galacticodyssey.combat.events.StatusEffectAppliedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PlayerStatQuery;

import java.util.Random;

/**
 * Processes all incoming damage events (hitscan, projectile, melee) through the unified
 * damage pipeline: hitbox multiplier → shield absorption → armor reduction → health deduction.
 * Publishes DamageDealtEvent, ShieldAbsorbEvent, EntityKilledEvent, and StatusEffectAppliedEvent.
 */
public class DamageSystem extends EntitySystem {

    public static final int PRIORITY = 8;

    private final EventBus eventBus;
    private final CombatDataRegistry combatData;
    private final WeaponDataRegistry weaponData;
    private final Random random = new Random();

    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ShieldComponent> SHIELD_M =
        ComponentMapper.getFor(ShieldComponent.class);
    private static final ComponentMapper<ArmorComponent> ARMOR_M =
        ComponentMapper.getFor(ArmorComponent.class);
    private static final ComponentMapper<HitboxComponent> HITBOX_M =
        ComponentMapper.getFor(HitboxComponent.class);
    private static final ComponentMapper<StatusEffectsComponent> STATUS_M =
        ComponentMapper.getFor(StatusEffectsComponent.class);

    public DamageSystem(EventBus eventBus, CombatDataRegistry combatData, WeaponDataRegistry weaponData) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.combatData = combatData;
        this.weaponData = weaponData;

        eventBus.subscribe(HitscanHitEvent.class, this::onHitscanHit);
        eventBus.subscribe(ProjectileHitEvent.class, this::onProjectileHit);
        eventBus.subscribe(MeleeHitEvent.class, this::onMeleeHit);
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private void onHitscanHit(HitscanHitEvent event) {
        processDamage(event.target, event.shooter, event.hitRegion,
            event.damage, event.damageType, event.ammoTypeId);
    }

    private void onProjectileHit(ProjectileHitEvent event) {
        // Projectiles have no specific hitRegion — treat as TORSO
        processDamage(event.target, event.shooter, HitRegion.TORSO,
            event.damage, event.damageType, event.ammoTypeId);
    }

    private void onMeleeHit(MeleeHitEvent event) {
        processDamage(event.target, event.attacker, event.hitRegion,
            event.damage, event.damageType, null);
    }

    // -------------------------------------------------------------------------
    // Core damage pipeline
    // -------------------------------------------------------------------------

    /**
     * Runs the full damage pipeline:
     * 1. Hitbox region multiplier
     * 2. Shield absorbs first (if not EMP_DISABLED)
     * 3. Armor reduces remaining damage (capped at maxArmorResistance; burning reduces effectiveness)
     * 4. Apply to health, publish DamageDealtEvent
     * 5. Kill check — publish EntityKilledEvent if HP <= 0
     * 6. Roll for status effect from ammo type
     */
    private void processDamage(Entity target, Entity attacker, HitRegion hitRegion,
                                float rawDamage, DamageType damageType, String ammoTypeId) {
        HealthComponent health = HEALTH_M.get(target);
        if (health == null || !health.alive) return;

        DamageConfigData config = combatData.getDamageConfig();

        // 1. Hitbox multiplier
        float multiplier = getHitboxMultiplier(target, hitRegion, config);
        float damage = rawDamage * multiplier;

        // 2. Shield absorption
        ShieldComponent shield = SHIELD_M.get(target);
        StatusEffectsComponent statusEffects = STATUS_M.get(target);
        boolean empDisabled = statusEffects != null && statusEffects.hasEffect(StatusEffectType.EMP_DISABLED);

        if (shield != null && shield.currentShield > 0f && !empDisabled) {
            float absorbed = Math.min(shield.currentShield, damage);
            shield.currentShield -= absorbed;
            shield.timeSinceLastHit = 0f;
            damage -= absorbed;
            eventBus.publish(new ShieldAbsorbEvent(target, absorbed, shield.currentShield));
        }

        if (damage <= 0f) return;

        // 3. Armor reduction
        ArmorComponent armor = ARMOR_M.get(target);
        if (armor != null) {
            float resistance = armor.getResistance(damageType, hitRegion);

            // Burning reduces armor effectiveness by the armorReduction field of the burning effect
            if (statusEffects != null && statusEffects.hasEffect(StatusEffectType.BURNING)) {
                StatusEffectData burningData = combatData.getStatusEffect(StatusEffectType.BURNING);
                if (burningData != null) {
                    resistance *= (1f - burningData.armorReduction);
                }
            }

            // Cap resistance
            if (config != null) {
                resistance = Math.min(resistance, config.maxArmorResistance);
            } else {
                resistance = Math.min(resistance, 0.85f);
            }

            damage *= (1f - resistance);
            armor.degradeSlot(hitRegion, rawDamage);
        }

        // Player outgoing-damage perk multiplier (player attackers only)
        if (attacker != null) {
            PlayerStatsComponent attackerStats = attacker.getComponent(PlayerStatsComponent.class);
            if (attackerStats != null) {
                damage *= PlayerStatQuery.getOutgoingDamageMultiplier(attackerStats, damageType);
            }
        }

        // 4. Apply to health
        health.currentHP -= damage;
        eventBus.publish(new DamageDealtEvent(target, attacker, damage, damageType, hitRegion));

        // 5. Kill check
        if (health.currentHP <= 0f) {
            health.currentHP = 0f;
            health.alive = false;
            eventBus.publish(new EntityKilledEvent(target, attacker));
        }

        // 6. Status effect roll from ammo
        if (ammoTypeId != null) {
            AmmoTypeData ammo = weaponData.getAmmoType(ammoTypeId);
            if (ammo != null && ammo.statusEffect != null && ammo.statusEffectChance > 0f) {
                if (random.nextFloat() < ammo.statusEffectChance) {
                    applyStatusEffect(target, attacker, ammo.statusEffect);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Returns the hitbox multiplier for the given region, preferring the HitboxComponent data
     * on the entity and falling back to the DamageConfigData global table, then to 1.0f.
     */
    private float getHitboxMultiplier(Entity target, HitRegion hitRegion, DamageConfigData config) {
        HitboxComponent hitbox = HITBOX_M.get(target);
        if (hitbox != null) {
            Float mult = hitbox.regionMultipliers.get(hitRegion);
            if (mult != null) return mult;
        }
        if (config != null) {
            Float mult = config.hitRegionMultipliers.get(hitRegion.name());
            if (mult != null) return mult;
        }
        return 1.0f;
    }

    /**
     * Applies (or stacks) a status effect on the target entity.
     * For use by other systems (e.g., MeleeSystem, burning traps).
     */
    public void applyStatusEffect(Entity target, Entity source, StatusEffectType type) {
        StatusEffectsComponent statusEffects = STATUS_M.get(target);
        if (statusEffects == null) return;

        StatusEffectData data = combatData.getStatusEffect(type);
        if (data == null) return;

        ActiveStatusEffect existing = statusEffects.getEffect(type);
        if (existing != null) {
            // Stack up to maxStacks, refresh duration
            if (existing.stacks < data.maxStacks) {
                existing.stacks++;
            }
            existing.remainingDuration = data.duration;
        } else {
            ActiveStatusEffect effect = new ActiveStatusEffect(
                type, data.duration, data.tickRate, data.magnitude, source
            );
            statusEffects.activeEffects.add(effect);
            eventBus.publish(new StatusEffectAppliedEvent(target, type, source));
        }
    }
}
