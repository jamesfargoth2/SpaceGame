package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.ActiveStatusEffect;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.data.StatusEffectData;
import com.galacticodyssey.combat.events.StatusEffectExpiredEvent;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatusEffectSystemTest {

    private EventBus eventBus;
    private CombatDataRegistry combatData;
    private StatusEffectSystem statusEffectSystem;
    private Engine engine;

    private final List<StatusEffectExpiredEvent> expiredEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        combatData = new CombatDataRegistry();

        DamageConfigData config = new DamageConfigData();
        config.defaultShieldRechargeDelay = 4.0f;
        combatData.setDamageConfig(config);

        // Register BLEEDING effect: 3 damage per tick, 1 second tick rate, 10s duration
        StatusEffectData bleedData = new StatusEffectData();
        bleedData.type = StatusEffectType.BLEEDING;
        bleedData.duration = 10f;
        bleedData.tickRate = 1.0f;
        bleedData.magnitude = 3f;
        bleedData.maxStacks = 3;
        combatData.registerStatusEffect(bleedData);

        statusEffectSystem = new StatusEffectSystem(eventBus, combatData);
        engine = new Engine();
        engine.addSystem(statusEffectSystem);

        eventBus.subscribe(StatusEffectExpiredEvent.class, expiredEvents::add);
    }

    @Test
    void bleedingTicksDamageOverTime() {
        // Entity starts with 100 HP and a bleeding effect
        Entity entity = new Entity();

        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        StatusEffectsComponent statusEffects = new StatusEffectsComponent();
        // Add a bleeding effect: 3 dmg per tick, 1s tick, 1 stack
        ActiveStatusEffect bleed = new ActiveStatusEffect(
            StatusEffectType.BLEEDING, 10f, 1.0f, 3f, null
        );
        statusEffects.activeEffects.add(bleed);

        entity.add(health);
        entity.add(statusEffects);
        engine.addEntity(entity);

        // Update for 1 second — should tick once and deal 3 damage
        engine.update(1.0f);

        assertEquals(97f, health.currentHP, 0.01f, "Bleeding should deal 3 damage after 1 second tick");
    }

    @Test
    void expiredEffectRemoved() {
        // Entity with a bleeding effect that has 2s duration
        Entity entity = new Entity();

        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        StatusEffectsComponent statusEffects = new StatusEffectsComponent();
        ActiveStatusEffect bleed = new ActiveStatusEffect(
            StatusEffectType.BLEEDING, 2.0f, 5.0f, 3f, null  // tickRate=5s so it won't tick
        );
        statusEffects.activeEffects.add(bleed);

        entity.add(health);
        entity.add(statusEffects);
        engine.addEntity(entity);

        // Update 1.0s — effect still active
        engine.update(1.0f);
        assertFalse(statusEffects.activeEffects.isEmpty(), "Effect should still be active after 1.0s");

        // Update 1.1s more — total 2.1s, duration=2.0s, effect should expire
        engine.update(1.1f);
        assertTrue(statusEffects.activeEffects.isEmpty(), "Effect should be removed after duration expires");

        // StatusEffectExpiredEvent should have been published
        assertEquals(1, expiredEvents.size(), "StatusEffectExpiredEvent should have been published");
        assertEquals(StatusEffectType.BLEEDING, expiredEvents.get(0).effectType,
            "Expired event should identify BLEEDING effect");
        assertEquals(entity, expiredEvents.get(0).target,
            "Expired event should reference the correct entity");
    }

    @Test
    void stacksMultiplyDamage() {
        // Entity with 3 stacks of bleeding (3 dmg per tick * 3 stacks = 9 per tick)
        Entity entity = new Entity();

        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        StatusEffectsComponent statusEffects = new StatusEffectsComponent();
        ActiveStatusEffect bleed = new ActiveStatusEffect(
            StatusEffectType.BLEEDING, 10f, 1.0f, 3f, null
        );
        bleed.stacks = 3;
        statusEffects.activeEffects.add(bleed);

        entity.add(health);
        entity.add(statusEffects);
        engine.addEntity(entity);

        // Update 1 second — should tick once for 3 * 3 = 9 damage
        engine.update(1.0f);

        assertEquals(91f, health.currentHP, 0.01f, "3 stacks of 3 dmg = 9 per tick → HP should be 91");
    }
}
