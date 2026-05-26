package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.ActiveStatusEffect;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.ShieldComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.combat.events.StatusEffectExpiredEvent;
import com.galacticodyssey.core.EventBus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ticks all active status effects on entities each frame:
 * - Shield recharge (after delay, not EMP_DISABLED)
 * - Decrements effect durations; removes and publishes StatusEffectExpiredEvent for expired ones
 * - Applies per-tick damage (magnitude * stacks); BURNING + EMP_DISABLED = 2x tick damage
 */
public class StatusEffectSystem extends IteratingSystem {

    public static final int PRIORITY = 9;

    private final EventBus eventBus;
    private final CombatDataRegistry combatData;

    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ShieldComponent> SHIELD_M =
        ComponentMapper.getFor(ShieldComponent.class);
    private static final ComponentMapper<StatusEffectsComponent> STATUS_M =
        ComponentMapper.getFor(StatusEffectsComponent.class);

    /** Reusable list to avoid allocations during effect expiry iteration. */
    private final List<ActiveStatusEffect> toRemove = new ArrayList<>();

    public StatusEffectSystem(EventBus eventBus, CombatDataRegistry combatData) {
        super(Family.all(StatusEffectsComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
        this.combatData = combatData;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StatusEffectsComponent statusEffects = STATUS_M.get(entity);
        HealthComponent health = HEALTH_M.get(entity);
        ShieldComponent shield = SHIELD_M.get(entity);

        // 1. Shield recharge
        if (shield != null) {
            boolean empDisabled = statusEffects.hasEffect(StatusEffectType.EMP_DISABLED);
            if (!empDisabled) {
                shield.timeSinceLastHit += deltaTime;
                float rechargeDelay = getRechargeDelay();
                if (shield.timeSinceLastHit >= rechargeDelay && shield.currentShield < shield.maxShield) {
                    shield.currentShield = Math.min(shield.maxShield,
                        shield.currentShield + shield.rechargeRate * deltaTime);
                }
            }
        }

        // Check combined effect flags for damage bonuses
        boolean hasBurning = statusEffects.hasEffect(StatusEffectType.BURNING);
        boolean hasEmp = statusEffects.hasEffect(StatusEffectType.EMP_DISABLED);

        // 2 & 3. Iterate effects: decrement duration, remove expired, tick damage
        toRemove.clear();

        for (ActiveStatusEffect effect : statusEffects.activeEffects) {
            effect.remainingDuration -= deltaTime;

            if (effect.remainingDuration <= 0f) {
                toRemove.add(effect);
                continue;
            }

            // Accumulate time for tick-based damage
            if (effect.tickRate > 0f && health != null && health.alive) {
                effect.tickAccumulator += deltaTime;
                while (effect.tickAccumulator >= effect.tickRate) {
                    effect.tickAccumulator -= effect.tickRate;
                    float tickDamage = effect.magnitude * effect.stacks;

                    // Burning + EMP combo = 2x damage
                    if (effect.type == StatusEffectType.BURNING && hasEmp) {
                        tickDamage *= 2f;
                    }

                    health.currentHP -= tickDamage;
                    if (health.currentHP <= 0f) {
                        health.currentHP = 0f;
                        health.alive = false;
                        eventBus.publish(new EntityKilledEvent(entity, effect.source));
                        break;
                    }
                }
            }
        }

        // Remove expired effects and publish events
        for (ActiveStatusEffect expired : toRemove) {
            statusEffects.activeEffects.remove(expired);
            eventBus.publish(new StatusEffectExpiredEvent(entity, expired.type));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private float getRechargeDelay() {
        DamageConfigData config = combatData.getDamageConfig();
        return config != null ? config.defaultShieldRechargeDelay : 4.0f;
    }
}
