package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.CombatEnums.MeleeCategory;
import com.galacticodyssey.combat.CombatEnums.MeleeState;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.CombatEnums.WeaponCategory;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.MeleeStateComponent;
import com.galacticodyssey.combat.components.MeleeWeaponComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.data.AmmoTypeData;
import com.galacticodyssey.combat.data.BarrelData;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.data.MeleeFrameData;
import com.galacticodyssey.combat.data.QualityTierData;
import com.galacticodyssey.combat.data.StatusEffectData;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.data.WeaponFrameData;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.systems.DamageSystem;
import com.galacticodyssey.combat.systems.HitscanSystem;
import com.galacticodyssey.combat.systems.MeleeSystem;
import com.galacticodyssey.combat.systems.ProjectileSystem;
import com.galacticodyssey.combat.systems.StatusEffectSystem;
import com.galacticodyssey.combat.systems.WeaponSwitchSystem;
import com.galacticodyssey.combat.systems.WeaponSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests that wire the full combat pipeline — all 7 systems
 * running in one Ashley {@link Engine} — and verify that events flow correctly from
 * input through weapon firing → hit detection → damage → kill.
 */
class CombatIntegrationTest {

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private EventBus eventBus;
    private WeaponDataRegistry weaponData;
    private CombatDataRegistry combatData;
    private Engine engine;

    private Entity player;
    private Entity enemy;

    private final List<DamageDealtEvent> damageDealtEvents = new ArrayList<>();
    private final List<EntityKilledEvent> entityKilledEvents = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Test data IDs
    // -------------------------------------------------------------------------

    private static final String RIFLE_FRAME_ID   = "it_rifle";
    private static final String RIFLE_BARREL_ID  = "it_barrel_std";
    private static final String RIFLE_AMMO_ID    = "it_ammo_9mm";
    private static final String BLADE_FRAME_ID   = "it_blade";
    private static final String QUALITY_COMMON   = "COMMON";

    // -------------------------------------------------------------------------
    // setUp
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        eventBus   = new EventBus();
        weaponData = new WeaponDataRegistry();
        combatData = new CombatDataRegistry();

        registerWeaponData();
        registerCombatData();

        buildEngine();
        buildEntities();

        // Capture events for assertions
        eventBus.subscribe(DamageDealtEvent.class, damageDealtEvents::add);
        eventBus.subscribe(EntityKilledEvent.class, entityKilledEvents::add);
    }

    // -------------------------------------------------------------------------
    // Test 1: Full ranged pipeline — fire → hitscan → damage
    // -------------------------------------------------------------------------

    /**
     * Player fires a SEMI hitscan rifle aimed directly at an enemy 9 m away.
     * After one engine tick:
     * - WeaponSystem should decrement ammo and publish WeaponFiredEvent
     * - HitscanSystem should detect the enemy on the ray and publish HitscanHitEvent
     * - DamageSystem should process the hit and publish DamageDealtEvent
     * - Enemy HP must have dropped below 100
     */
    @Test
    void fullRangedCombatPipeline_fireHitDamage() {
        // Aim direction: straight forward along -Z axis toward enemy at (0,1,-10)
        CombatInputComponent input = player.getComponent(CombatInputComponent.class);
        input.aimDirection.set(0f, 0f, -1f);
        input.fireRequested = true;

        // Enemy at (0, 1, -10): 9 m away from player at (0, 1, 0)
        // Already set in buildEntities()

        engine.update(0.016f);

        assertFalse(damageDealtEvents.isEmpty(),
            "DamageDealtEvent should have been published after hitscan hit");

        DamageDealtEvent evt = damageDealtEvents.get(0);
        assertTrue(evt.finalDamage > 0f,
            "Final damage should be positive; got " + evt.finalDamage);
        assertEquals(enemy, evt.target,
            "Damage target should be the enemy entity");

        HealthComponent enemyHp = enemy.getComponent(HealthComponent.class);
        assertTrue(enemyHp.currentHP < 100f,
            "Enemy HP should have decreased from 100; remaining: " + enemyHp.currentHP);
    }

    // -------------------------------------------------------------------------
    // Test 2: Full melee pipeline — switch slot → wind-up → ACTIVE → damage
    // -------------------------------------------------------------------------

    /**
     * Player requests a switch to the MELEE slot (index 2), waits for the switch
     * timer, then requests an OVERHEAD attack with the enemy at melee range.
     * After advancing through WIND_UP → ACTIVE, a MeleeHitEvent is published and
     * processed by DamageSystem, causing damage to the enemy.
     */
    @Test
    void fullMeleeCombatPipeline_switchAttackDamage() {
        // Move enemy to melee reach (1.0 m, same Y as player, directly in front)
        TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
        enemyTransform.position.set(0f, 1f, -1.5f);

        // Request weapon switch to melee slot (slot 2 = MELEE)
        CombatInputComponent input = player.getComponent(CombatInputComponent.class);
        input.aimDirection.set(0f, 0f, -1f);
        input.switchSlotRequested = 2;

        // Tick once to start the switch timer (MELEE switchTime = 0.2 s)
        engine.update(0.016f);

        WeaponInventoryComponent inventory = player.getComponent(WeaponInventoryComponent.class);
        assertTrue(inventory.switching, "Inventory should be in switching state");

        // Advance past the switch timer (0.2 s) in one large step
        engine.update(0.3f);

        assertFalse(inventory.switching, "Switch should have completed");
        assertEquals(2, inventory.activeSlotIndex, "Active slot should now be MELEE (index 2)");

        // Now request an OVERHEAD melee attack
        input.meleeAttackRequested = true;
        input.meleeAttackDirection = AttackDirection.OVERHEAD;

        // Tick to initiate WIND_UP
        engine.update(0.016f);

        MeleeStateComponent meleeState = player.getComponent(MeleeStateComponent.class);
        assertEquals(MeleeState.WIND_UP, meleeState.currentState,
            "Melee FSM should have entered WIND_UP");

        // Clear the request so it isn't re-consumed on subsequent ticks
        input.meleeAttackRequested = false;

        // LIGHT weight class: windUpTime = 0.15 s, activeTime = 0.1 s
        // Advance past wind-up
        engine.update(0.2f);

        // Should now be in ACTIVE or already through it — either way damage must have been dealt
        // (ACTIVE runs performHitDetection on entry; a further tick may push into RECOVERY)
        engine.update(0.016f);

        assertFalse(damageDealtEvents.isEmpty(),
            "DamageDealtEvent should have been published after melee hit");

        DamageDealtEvent evt = damageDealtEvents.get(0);
        assertTrue(evt.finalDamage > 0f,
            "Melee final damage should be positive; got " + evt.finalDamage);
        assertEquals(enemy, evt.target,
            "Damage target should be the enemy entity");

        HealthComponent enemyHp = enemy.getComponent(HealthComponent.class);
        assertTrue(enemyHp.currentHP < 100f,
            "Enemy HP should have decreased; remaining: " + enemyHp.currentHP);
    }

    // -------------------------------------------------------------------------
    // Test 3: Player can die from incoming damage
    // -------------------------------------------------------------------------

    /**
     * Player has only 5 HP. Publishing a HitscanHitEvent targeting the player with
     * 50 damage should kill the player: EntityKilledEvent is published and alive=false.
     */
    @Test
    void playerCanDieFromDamage() {
        // Set player HP to 5
        HealthComponent playerHp = player.getComponent(HealthComponent.class);
        playerHp.currentHP = 5f;
        playerHp.alive = true;

        // Publish a lethal hitscan hit directly — bypasses weapon/hitscan systems,
        // tests that DamageSystem responds correctly to the event.
        eventBus.publish(new HitscanHitEvent(
            enemy,                             // shooter = enemy
            player,                            // target  = player
            new Vector3(0f, 1f, 0f),           // hit point
            new Vector3(0f, 0f, 1f),           // hit normal
            HitRegion.TORSO,
            50f,                               // raw damage >> remaining HP
            DamageType.BALLISTIC,
            null                               // no ammo-type status effect
        ));

        // DamageSystem is subscribed to HitscanHitEvent and processes synchronously
        assertEquals(1, entityKilledEvents.size(),
            "EntityKilledEvent should have been published for the player");

        EntityKilledEvent killed = entityKilledEvents.get(0);
        assertEquals(player, killed.target, "The killed entity should be the player");
        assertFalse(playerHp.alive, "Player alive flag should be false after lethal hit");
        assertEquals(0f, playerHp.currentHP, 0.001f,
            "Player HP should be clamped to 0 after death");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Populates WeaponDataRegistry with minimal in-memory stubs. */
    private void registerWeaponData() {
        // Rifle frame — SEMI, hitscan, 25 base damage
        WeaponFrameData rifle = new WeaponFrameData();
        rifle.id          = RIFLE_FRAME_ID;
        rifle.category    = WeaponCategory.RIFLE;
        rifle.baseDamage  = 25f;
        rifle.baseFireRate = 2f;
        rifle.baseSpread  = 0f;   // zero spread so ray goes exactly where aimed
        rifle.baseRecoil  = 0.5f;
        rifle.magSize     = 30;
        rifle.reloadTime  = 2.0f;
        rifle.range       = 100f;
        rifle.firingMode  = FiringMode.SEMI;
        rifle.hitscan     = true;
        weaponData.registerFrame(rifle);

        // Standard barrel (neutral multipliers)
        BarrelData barrel = new BarrelData();
        barrel.id               = RIFLE_BARREL_ID;
        barrel.damageMultiplier = 1.0f;
        barrel.rangeMultiplier  = 1.0f;
        barrel.spreadMultiplier = 1.0f;
        barrel.recoilMultiplier = 1.0f;
        weaponData.registerBarrel(barrel);

        // Ammo type — BALLISTIC, neutral multiplier, no status effect
        AmmoTypeData ammo = new AmmoTypeData();
        ammo.id               = RIFLE_AMMO_ID;
        ammo.damageType       = DamageType.BALLISTIC;
        ammo.damageMultiplier = 1.0f;
        ammo.statusEffect     = null;
        ammo.statusEffectChance = 0f;
        weaponData.registerAmmoType(ammo);

        // Quality tier COMMON — neutral global multiplier
        QualityTierData common = new QualityTierData();
        common.tier             = QualityTier.COMMON;
        common.globalMultiplier = 1.0f;
        weaponData.registerQuality(common);

        // Melee blade frame — LIGHT weight class for fast FSM transitions
        MeleeFrameData blade = new MeleeFrameData();
        blade.id                  = BLADE_FRAME_ID;
        blade.category            = MeleeCategory.BLADE;
        blade.baseDamage          = 30f;
        blade.baseSpeed           = 1.5f;
        blade.baseReach           = 2.0f;   // generous reach to ensure hit at 1.5 m
        blade.baseBlockEfficiency = 0.6f;
        blade.weight              = 1.5f;
        blade.damageType          = DamageType.MELEE;
        blade.weightClass         = WeightClass.LIGHT;
        weaponData.registerMeleeFrame(blade);
    }

    /** Populates CombatDataRegistry with minimal damage config and bleeding effect stub. */
    private void registerCombatData() {
        DamageConfigData config = new DamageConfigData();
        config.hitRegionMultipliers.put("HEAD",  2.0f);
        config.hitRegionMultipliers.put("TORSO", 1.0f);
        config.hitRegionMultipliers.put("ARMS",  0.75f);
        config.hitRegionMultipliers.put("LEGS",  0.75f);
        config.maxArmorResistance          = 0.85f;
        config.defaultShieldRechargeDelay  = 4.0f;
        config.wrongBlockMitigation        = 0.3f;
        config.exhaustionAttackPenalty     = 0.4f;
        config.comboDamageBonus            = 0.1f;
        config.comboStaminaPenalty         = 0.2f;
        combatData.setDamageConfig(config);

        // Bleeding status effect (not triggered in these tests, but registered for completeness)
        StatusEffectData bleeding = new StatusEffectData();
        bleeding.type      = CombatEnums.StatusEffectType.BLEEDING;
        bleeding.duration  = 5f;
        bleeding.tickRate  = 1f;
        bleeding.magnitude = 3f;
        bleeding.maxStacks = 3;
        combatData.registerStatusEffect(bleeding);
    }

    /** Creates and populates the Ashley engine with all 7 combat systems. */
    private void buildEngine() {
        engine = new Engine();
        engine.addSystem(new WeaponSwitchSystem(eventBus, weaponData));
        engine.addSystem(new WeaponSystem(eventBus));
        engine.addSystem(new MeleeSystem(eventBus, combatData));
        engine.addSystem(new HitscanSystem(eventBus));
        engine.addSystem(new ProjectileSystem(eventBus));
        engine.addSystem(new DamageSystem(eventBus, combatData, weaponData));
        engine.addSystem(new StatusEffectSystem(eventBus, combatData));
    }

    /**
     * Builds the player and enemy entities and adds them to the engine.
     *
     * <p>Player: position (0,1,0), ranged slot 0 = rifle, melee slot 2 = blade.
     * <p>Enemy: position (0,1,-10), 100 HP, hitbox.
     */
    private void buildEntities() {
        player = buildPlayer();
        enemy  = buildEnemy();
        engine.addEntity(player);
        engine.addEntity(enemy);
    }

    private Entity buildPlayer() {
        Entity e = new Entity();

        // Position
        TransformComponent transform = new TransformComponent();
        transform.position.set(0f, 1f, 0f);
        e.add(transform);

        // Player identity components
        e.add(new PlayerTagComponent());
        e.add(new FPSCameraComponent());
        e.add(new MovementStateComponent());

        // Combat input — aim forward along -Z
        CombatInputComponent input = new CombatInputComponent();
        input.aimDirection.set(0f, 0f, -1f);
        e.add(input);

        // Weapon inventory — slot 0 = ranged rifle, slot 2 = melee blade
        WeaponInventoryComponent inventory = new WeaponInventoryComponent();
        inventory.slots[0] = WeaponAssembly.ranged(
            RIFLE_FRAME_ID, RIFLE_BARREL_ID, RIFLE_AMMO_ID, new String[0], QualityTier.COMMON);
        inventory.slots[2] = WeaponAssembly.melee(BLADE_FRAME_ID, QualityTier.COMMON);
        inventory.activeSlotIndex = 0;
        e.add(inventory);

        // Ranged weapon component — pre-configured to match rifle frame
        RangedWeaponComponent ranged = new RangedWeaponComponent();
        ranged.damage      = 25f;
        ranged.fireRate    = 2f;
        ranged.spread      = 0f;
        ranged.range       = 100f;
        ranged.recoil      = 0.5f;
        ranged.magSize     = 30;
        ranged.currentAmmo = 30;
        ranged.reloadTime  = 2.0f;
        ranged.firingMode  = FiringMode.SEMI;
        ranged.hitscan     = true;
        ranged.damageType  = DamageType.BALLISTIC;
        ranged.ammoTypeId  = RIFLE_AMMO_ID;
        e.add(ranged);

        // Melee weapon component — pre-configured to match blade frame
        MeleeWeaponComponent melee = new MeleeWeaponComponent();
        melee.baseDamage      = 30f;
        melee.swingSpeed      = 1.5f;
        melee.reach           = 2.0f;
        melee.blockEfficiency = 0.6f;
        melee.damageType      = DamageType.MELEE;
        melee.weightClass     = WeightClass.LIGHT;
        e.add(melee);

        // Melee FSM state (starts IDLE)
        e.add(new MeleeStateComponent());

        // Health and hitbox for player (so player can be targeted in test 3)
        HealthComponent hp = new HealthComponent();
        hp.currentHP = 100f;
        hp.maxHP     = 100f;
        hp.alive     = true;
        e.add(hp);

        e.add(new HitboxComponent());
        e.add(new StatusEffectsComponent());

        return e;
    }

    private Entity buildEnemy() {
        Entity e = new Entity();

        // Position — 9 m in front of player along -Z
        TransformComponent transform = new TransformComponent();
        transform.position.set(0f, 1f, -10f);
        e.add(transform);

        // Health
        HealthComponent hp = new HealthComponent();
        hp.currentHP = 100f;
        hp.maxHP     = 100f;
        hp.alive     = true;
        e.add(hp);

        // Hitbox and status effects (required by DamageSystem / HitscanSystem)
        e.add(new HitboxComponent());
        e.add(new StatusEffectsComponent());

        return e;
    }
}
