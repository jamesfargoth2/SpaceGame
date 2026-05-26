package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.MeleeState;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.MeleeStateComponent;
import com.galacticodyssey.combat.components.MeleeWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.events.MeleeBlockEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MeleeSystem}.
 */
class MeleeSystemTest {

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    private EventBus eventBus;
    private CombatDataRegistry combatData;
    private DamageConfigData config;
    private MeleeSystem meleeSystem;
    private Engine engine;

    /** Attacker entity and its components. */
    private Entity attacker;
    private MeleeStateComponent attackerMeleeState;
    private MeleeWeaponComponent attackerWeapon;
    private CombatInputComponent attackerInput;
    private TransformComponent attackerTransform;
    private MovementStateComponent attackerMovement;
    private WeaponInventoryComponent attackerInventory;

    /** Target entity and its components. */
    private Entity target;
    private TransformComponent targetTransform;
    private HitboxComponent targetHitbox;
    private HealthComponent targetHealth;

    /** Captured events. */
    private final List<MeleeHitEvent> hitEvents = new ArrayList<>();
    private final List<MeleeBlockEvent> blockEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();

        // Build a default DamageConfigData
        config = new DamageConfigData();
        config.wrongBlockMitigation = 0.3f;
        config.exhaustionAttackPenalty = 0.4f;
        config.comboDamageBonus = 0.1f;
        config.comboStaminaPenalty = 0.2f;
        config.maxComboHits = 3;

        combatData = new CombatDataRegistry();
        combatData.setDamageConfig(config);

        meleeSystem = new MeleeSystem(eventBus, combatData);
        engine = new Engine();
        engine.addSystem(meleeSystem);

        // Subscribe for assertions
        eventBus.subscribe(MeleeHitEvent.class, hitEvents::add);
        eventBus.subscribe(MeleeBlockEvent.class, blockEvents::add);

        // ---- Attacker entity ----
        attacker = buildAttacker(0f, 0f, 0f);

        // ---- Target entity ----
        target = buildTarget(0f, 0f, -1.5f);
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    /**
     * Creates a full attacker entity (LIGHT MELEE weapon, melee slot active) at the given position.
     */
    private Entity buildAttacker(float x, float y, float z) {
        Entity e = new Entity();

        // Transform
        TransformComponent t = new TransformComponent();
        t.position.set(x, y, z);

        // MeleeState
        MeleeStateComponent ms = new MeleeStateComponent();

        // MeleeWeapon — LIGHT, damage=40, reach=2, OVERHEAD modifier=1.3
        MeleeWeaponComponent mw = new MeleeWeaponComponent();
        mw.baseDamage = 40f;
        mw.reach = 2f;
        mw.damageType = DamageType.MELEE;
        mw.weightClass = com.galacticodyssey.combat.CombatEnums.WeightClass.LIGHT;
        mw.directionalModifiers.put(AttackDirection.OVERHEAD, 1.3f);

        // CombatInput — aim toward -Z (forward into target)
        CombatInputComponent ci = new CombatInputComponent();
        ci.aimDirection.set(0f, 0f, -1f);

        // MovementState — full stamina, not exhausted
        MovementStateComponent mov = new MovementStateComponent();
        mov.currentStamina = 100f;
        mov.isExhausted = false;

        // WeaponInventory — active slot = MELEE
        WeaponInventoryComponent inv = new WeaponInventoryComponent();
        inv.activeSlotIndex = WeaponSlot.MELEE.index;

        e.add(t);
        e.add(ms);
        e.add(mw);
        e.add(ci);
        e.add(mov);
        e.add(inv);

        // Cache refs if this is the primary attacker
        if (attacker == null) {
            attackerTransform = t;
            attackerMeleeState = ms;
            attackerWeapon = mw;
            attackerInput = ci;
            attackerMovement = mov;
            attackerInventory = inv;
        }

        engine.addEntity(e);

        // Assign cached refs now (called before assignments above for first entity)
        attackerTransform = t;
        attackerMeleeState = ms;
        attackerWeapon = mw;
        attackerInput = ci;
        attackerMovement = mov;
        attackerInventory = inv;

        return e;
    }

    /**
     * Creates a target entity with HitboxComponent and HealthComponent at the given position.
     */
    private Entity buildTarget(float x, float y, float z) {
        Entity e = new Entity();

        TransformComponent t = new TransformComponent();
        t.position.set(x, y, z);
        targetTransform = t;

        HitboxComponent hb = new HitboxComponent();
        targetHitbox = hb;

        HealthComponent hp = new HealthComponent();
        hp.currentHP = 100f;
        hp.maxHP = 100f;
        hp.alive = true;
        targetHealth = hp;

        e.add(t);
        e.add(hb);
        e.add(hp);
        engine.addEntity(e);
        return e;
    }

    /**
     * Adds a full melee-capable blocking setup to the target entity.
     * After this call the target has MeleeStateComponent, MeleeWeaponComponent,
     * CombatInputComponent, MovementStateComponent, and WeaponInventoryComponent.
     */
    private void makeTargetABlocker(AttackDirection blockDir) {
        MeleeStateComponent ms = new MeleeStateComponent();
        ms.currentState = MeleeState.BLOCKING;
        ms.blockDirection = blockDir;
        target.add(ms);

        MeleeWeaponComponent mw = new MeleeWeaponComponent();
        mw.weightClass = com.galacticodyssey.combat.CombatEnums.WeightClass.LIGHT;
        mw.baseDamage = 30f;
        mw.reach = 2f;
        mw.damageType = DamageType.MELEE;
        target.add(mw);

        CombatInputComponent ci = new CombatInputComponent();
        ci.blockHeld = true;
        ci.blockDirection = blockDir;
        target.add(ci);

        MovementStateComponent mov = new MovementStateComponent();
        target.add(mov);

        WeaponInventoryComponent inv = new WeaponInventoryComponent();
        inv.activeSlotIndex = WeaponSlot.MELEE.index;
        target.add(inv);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Requesting a melee attack from IDLE with OVERHEAD direction must transition
     * the attacker to WIND_UP with direction=OVERHEAD.
     */
    @Test
    void attackTransitionsToWindUp() {
        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.OVERHEAD;

        engine.update(0.01f);

        assertEquals(MeleeState.WIND_UP, attackerMeleeState.currentState,
            "After requesting attack from IDLE the state must be WIND_UP");
        assertEquals(AttackDirection.OVERHEAD, attackerMeleeState.attackDirection,
            "Attack direction must be set to OVERHEAD");
    }

    /**
     * A WIND_UP timer that has nearly expired must transition to ACTIVE on the next tick.
     */
    @Test
    void windUpTransitionsToActive() {
        // Put attacker into WIND_UP with a tiny remaining timer
        attackerMeleeState.currentState = MeleeState.WIND_UP;
        attackerMeleeState.attackDirection = AttackDirection.OVERHEAD;
        attackerMeleeState.comboCounter = 1;
        // Timer so small that a single 0.05-second tick will expire it
        attackerMeleeState.stateTimer = 0.01f;

        engine.update(0.05f);

        assertEquals(MeleeState.ACTIVE, attackerMeleeState.currentState,
            "WIND_UP with expired timer must transition to ACTIVE");
    }

    /**
     * While in ACTIVE with a target in range and in front, a MeleeHitEvent must be published.
     */
    @Test
    void activePhasePublishesMeleeHit() {
        // Place target 1.5 m in front (within reach=2)
        targetTransform.position.set(0f, 0f, -1.5f);

        // Put attacker straight into ACTIVE
        attackerMeleeState.currentState = MeleeState.ACTIVE;
        attackerMeleeState.attackDirection = AttackDirection.OVERHEAD;
        attackerMeleeState.comboCounter = 1;
        attackerMeleeState.stateTimer = 1.0f; // long enough so it doesn't expire

        // Single tick just to run processEntity; timer won't expire
        engine.update(0.01f);

        assertFalse(hitEvents.isEmpty(), "MeleeHitEvent must be published for target in range and facing");
        MeleeHitEvent hit = hitEvents.get(0);
        assertEquals(attacker, hit.attacker);
        assertEquals(target, hit.target);
        assertEquals(AttackDirection.OVERHEAD, hit.direction);
        // damage = 40 * 1.3 (OVERHEAD mod) * (1 + 0.1 * 0) = 52
        assertEquals(52f, hit.damage, 0.01f, "OVERHEAD hit damage must be baseDamage * directionalMod");
    }

    /**
     * A target that is BLOCKING the same direction as the attack must trigger a perfect block:
     * MeleeBlockEvent with perfectBlock=true, and the attacker must be STAGGERED.
     */
    @Test
    void blockPublishesBlockEvent() {
        // Target blocks OVERHEAD (same as incoming attack direction)
        makeTargetABlocker(AttackDirection.OVERHEAD);

        targetTransform.position.set(0f, 0f, -1.5f);

        // Put attacker in ACTIVE for the OVERHEAD attack
        attackerMeleeState.currentState = MeleeState.ACTIVE;
        attackerMeleeState.attackDirection = AttackDirection.OVERHEAD;
        attackerMeleeState.comboCounter = 1;
        attackerMeleeState.stateTimer = 1.0f;

        engine.update(0.01f);

        // Must have published a block event with perfectBlock=true
        assertFalse(blockEvents.isEmpty(), "MeleeBlockEvent must be published when target is blocking");
        MeleeBlockEvent blockEvent = blockEvents.get(0);
        assertTrue(blockEvent.perfectBlock,
            "Block must be perfect when block direction matches attack direction");
        assertEquals(attacker, blockEvent.attacker);
        assertEquals(target, blockEvent.blocker);

        // Attacker must be STAGGERED
        assertEquals(MeleeState.STAGGERED, attackerMeleeState.currentState,
            "Attacker must enter STAGGERED state after a perfect block");
        assertTrue(attackerMeleeState.stateTimer > 0f,
            "STAGGERED timer must be positive");

        // No damage hit event should be published for a perfect block
        assertTrue(hitEvents.isEmpty(),
            "No MeleeHitEvent should be published for a perfect block");
    }

    /**
     * When the attacker is exhausted the WIND_UP time must be longer than the base value
     * (exhaustionAttackPenalty = 0.4 → speedMult = 1 / (1 - 0.4) ≈ 1.667x).
     */
    @Test
    void exhaustionSlowsAttack() {
        float baseWindUp = attackerWeapon.weightClass.windUpTime; // LIGHT = 0.15

        // Reset stamina and mark exhausted
        attackerMovement.currentStamina = 0f;
        attackerMovement.isExhausted = true;

        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.LEFT;

        engine.update(0.01f);

        assertEquals(MeleeState.WIND_UP, attackerMeleeState.currentState);

        // The timer should be larger than the base wind-up time
        assertTrue(attackerMeleeState.stateTimer > baseWindUp,
            "Exhausted wind-up timer (" + attackerMeleeState.stateTimer +
            ") must exceed base wind-up time (" + baseWindUp + ")");
    }

    /**
     * During RECOVERY, requesting an attack in a *different* direction (with canCombo=true)
     * must set queuedDirection to the new direction.
     */
    @Test
    void comboAllowedOnDifferentDirection() {
        attackerMeleeState.currentState = MeleeState.RECOVERY;
        attackerMeleeState.attackDirection = AttackDirection.LEFT;
        attackerMeleeState.canCombo = true;
        attackerMeleeState.stateTimer = 1.0f; // will not expire

        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.RIGHT; // different direction

        engine.update(0.01f);

        assertEquals(AttackDirection.RIGHT, attackerMeleeState.queuedDirection,
            "A different-direction attack during RECOVERY must be queued as a combo");
    }

    /**
     * During RECOVERY, requesting an attack in the *same* direction (with canCombo=true)
     * must NOT set queuedDirection (same-direction combos are rejected).
     */
    @Test
    void sameDirectionComboRejected() {
        attackerMeleeState.currentState = MeleeState.RECOVERY;
        attackerMeleeState.attackDirection = AttackDirection.LEFT;
        attackerMeleeState.canCombo = true;
        attackerMeleeState.stateTimer = 1.0f; // will not expire

        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.LEFT; // same direction

        engine.update(0.01f);

        assertNull(attackerMeleeState.queuedDirection,
            "Same-direction repeat during RECOVERY must not queue a combo");
    }
}
