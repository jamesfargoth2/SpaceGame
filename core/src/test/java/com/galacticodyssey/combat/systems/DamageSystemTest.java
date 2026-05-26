package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.ShieldComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.core.EventBus;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DamageSystemTest {

    private EventBus eventBus;
    private CombatDataRegistry combatData;
    private WeaponDataRegistry weaponData;
    private DamageSystem damageSystem;
    private Engine engine;

    private Entity shooter;
    private Entity target;

    private final List<DamageDealtEvent> damageDealtEvents = new ArrayList<>();
    private final List<EntityKilledEvent> entityKilledEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        combatData = new CombatDataRegistry();
        weaponData = new WeaponDataRegistry();

        // Set up default damage config with standard hitbox multipliers
        DamageConfigData config = new DamageConfigData();
        config.hitRegionMultipliers.put("HEAD", 2.0f);
        config.hitRegionMultipliers.put("TORSO", 1.0f);
        config.hitRegionMultipliers.put("ARMS", 0.75f);
        config.hitRegionMultipliers.put("LEGS", 0.75f);
        config.maxArmorResistance = 0.85f;
        config.defaultShieldRechargeDelay = 4.0f;
        combatData.setDamageConfig(config);

        damageSystem = new DamageSystem(eventBus, combatData, weaponData);
        engine = new Engine();
        engine.addSystem(damageSystem);

        shooter = new Entity();
        target = new Entity();

        // Subscribe to events for assertions
        eventBus.subscribe(DamageDealtEvent.class, damageDealtEvents::add);
        eventBus.subscribe(EntityKilledEvent.class, entityKilledEvents::add);
    }

    @Test
    void headshotDealsTwoDamage() {
        // Target has health and hitbox
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        HitboxComponent hitbox = new HitboxComponent();
        StatusEffectsComponent statusEffects = new StatusEffectsComponent();

        target.add(health);
        target.add(hitbox);
        target.add(statusEffects);
        engine.addEntity(target);

        // Fire HitscanHitEvent at HEAD with 20 damage
        HitscanHitEvent event = new HitscanHitEvent(
            shooter, target, new Vector3(0, 0, 0), new Vector3(0, 1, 0),
            HitRegion.HEAD, 20f, DamageType.BALLISTIC, null
        );
        eventBus.publish(event);

        // DamageDealtEvent should have 40 damage (2.0x head multiplier)
        assertEquals(1, damageDealtEvents.size(), "Expected exactly one DamageDealtEvent");
        assertEquals(40f, damageDealtEvents.get(0).finalDamage, 0.01f,
            "Headshot should deal 2x damage (20 * 2.0 = 40)");
    }

    @Test
    void shieldAbsorbsDamageBeforeHealth() {
        // Target has 30 shield and 100 HP
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        ShieldComponent shield = new ShieldComponent();
        shield.currentShield = 30f;
        shield.maxShield = 30f;

        HitboxComponent hitbox = new HitboxComponent();
        StatusEffectsComponent statusEffects = new StatusEffectsComponent();

        target.add(health);
        target.add(shield);
        target.add(hitbox);
        target.add(statusEffects);
        engine.addEntity(target);

        // Fire 50 damage at TORSO (1.0x multiplier = 50 damage)
        HitscanHitEvent event = new HitscanHitEvent(
            shooter, target, new Vector3(0, 0, 0), new Vector3(0, 1, 0),
            HitRegion.TORSO, 50f, DamageType.BALLISTIC, null
        );
        eventBus.publish(event);

        // Shield should be 0, HP should be 80 (100 - (50 - 30) = 80)
        assertEquals(0f, shield.currentShield, 0.01f, "Shield should be fully depleted");
        assertEquals(80f, health.currentHP, 0.01f, "HP should be 80 after shield absorbed 30 of 50 damage");
    }

    @Test
    void armorReducesDamage() {
        // Target has 50% BALLISTIC resistance on TORSO
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        ArmorComponent armor = new ArmorComponent();
        armor.resistances.get(HitRegion.TORSO).put(DamageType.BALLISTIC, 0.5f);

        HitboxComponent hitbox = new HitboxComponent();
        StatusEffectsComponent statusEffects = new StatusEffectsComponent();

        target.add(health);
        target.add(armor);
        target.add(hitbox);
        target.add(statusEffects);
        engine.addEntity(target);

        // Fire 40 BALLISTIC damage at TORSO
        HitscanHitEvent event = new HitscanHitEvent(
            shooter, target, new Vector3(0, 0, 0), new Vector3(0, 1, 0),
            HitRegion.TORSO, 40f, DamageType.BALLISTIC, null
        );
        eventBus.publish(event);

        // 40 * (1 - 0.5) = 20 damage dealt
        assertEquals(1, damageDealtEvents.size(), "Expected exactly one DamageDealtEvent");
        assertEquals(20f, damageDealtEvents.get(0).finalDamage, 0.01f,
            "Armor should reduce 40 ballistic damage by 50% to 20");
    }

    @Test
    void entityKilledAtZeroHP() {
        // Target has only 10 HP
        HealthComponent health = new HealthComponent();
        health.currentHP = 10f;
        health.maxHP = 10f;
        health.alive = true;

        HitboxComponent hitbox = new HitboxComponent();
        StatusEffectsComponent statusEffects = new StatusEffectsComponent();

        target.add(health);
        target.add(hitbox);
        target.add(statusEffects);
        engine.addEntity(target);

        // Fire 50 damage at TORSO (overkill)
        HitscanHitEvent event = new HitscanHitEvent(
            shooter, target, new Vector3(0, 0, 0), new Vector3(0, 1, 0),
            HitRegion.TORSO, 50f, DamageType.BALLISTIC, null
        );
        eventBus.publish(event);

        // EntityKilledEvent should fire, alive should be false
        assertEquals(1, entityKilledEvents.size(), "Expected EntityKilledEvent to be published");
        assertFalse(health.alive, "Target should be marked as not alive");
    }

    @Test
    void meleeHitProcessedThroughPipeline() {
        // Target with hitbox (HEAD = 2.0x)
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        HitboxComponent hitbox = new HitboxComponent();
        StatusEffectsComponent statusEffects = new StatusEffectsComponent();

        target.add(health);
        target.add(hitbox);
        target.add(statusEffects);
        engine.addEntity(target);

        // MeleeHitEvent OVERHEAD/HEAD with 40 damage
        MeleeHitEvent event = new MeleeHitEvent(
            shooter, target, AttackDirection.OVERHEAD, HitRegion.HEAD, 40f, DamageType.MELEE
        );
        eventBus.publish(event);

        // 40 * 2.0 (head multiplier) = 80 damage
        assertEquals(1, damageDealtEvents.size(), "Expected exactly one DamageDealtEvent");
        assertEquals(80f, damageDealtEvents.get(0).finalDamage, 0.01f,
            "Overhead head melee hit should deal 2x damage (40 * 2.0 = 80)");
    }
}
