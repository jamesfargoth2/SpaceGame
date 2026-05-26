package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.CombatEnums.MeleeState;
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

/**
 * FSM-driven melee combat system. Iterates over all entities that have a melee weapon equipped
 * and advances their melee state machine each frame.
 *
 * <p>States:
 * <ul>
 *   <li><b>IDLE</b> — waits for block input or attack request</li>
 *   <li><b>WIND_UP</b> — pre-attack animation window; exhaustion extends this phase</li>
 *   <li><b>ACTIVE</b> — hit-detection window; publishes {@link MeleeHitEvent} or
 *       {@link MeleeBlockEvent}</li>
 *   <li><b>RECOVERY</b> — post-attack; accepts combo input for a different direction</li>
 *   <li><b>BLOCKING</b> — continuously tracks block direction from input</li>
 *   <li><b>STAGGERED</b> — frozen after a perfect block is received; decays to IDLE</li>
 * </ul>
 *
 * <p>Priority: {@value #PRIORITY}.
 */
public class MeleeSystem extends IteratingSystem {

    public static final int PRIORITY = 5;

    /**
     * Dot-product threshold for "facing" check. A value of 0.5 corresponds to ±60° cone
     * in front of the attacker.
     */
    private static final float FACING_DOT_THRESHOLD = 0.5f;

    private final EventBus eventBus;
    private final CombatDataRegistry combatData;

    /** Reference set via {@link #addedToEngine} and cleared on {@link #removedFromEngine}. */
    private Engine engine;

    // -------------------------------------------------------------------------
    // Component mappers
    // -------------------------------------------------------------------------
    private static final ComponentMapper<MeleeStateComponent> MELEE_STATE_M =
        ComponentMapper.getFor(MeleeStateComponent.class);
    private static final ComponentMapper<MeleeWeaponComponent> MELEE_WEAPON_M =
        ComponentMapper.getFor(MeleeWeaponComponent.class);
    private static final ComponentMapper<CombatInputComponent> COMBAT_INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<WeaponInventoryComponent> INVENTORY_M =
        ComponentMapper.getFor(WeaponInventoryComponent.class);
    private static final ComponentMapper<MovementStateComponent> MOVEMENT_M =
        ComponentMapper.getFor(MovementStateComponent.class);
    private static final ComponentMapper<HitboxComponent> HITBOX_M =
        ComponentMapper.getFor(HitboxComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    /** Family of entities that can be melee targets. */
    private static final Family TARGET_FAMILY =
        Family.all(TransformComponent.class, HitboxComponent.class, HealthComponent.class).get();

    // -------------------------------------------------------------------------
    // Scratch vectors (single-threaded use only)
    // -------------------------------------------------------------------------
    private final Vector3 toTarget = new Vector3();
    private final Vector3 attackerForward = new Vector3();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public MeleeSystem(EventBus eventBus, CombatDataRegistry combatData) {
        super(Family.all(
            MeleeStateComponent.class,
            MeleeWeaponComponent.class,
            CombatInputComponent.class,
            TransformComponent.class,
            WeaponInventoryComponent.class
        ).get(), PRIORITY);
        this.eventBus = eventBus;
        this.combatData = combatData;
    }

    // -------------------------------------------------------------------------
    // Engine lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    // -------------------------------------------------------------------------
    // Per-entity FSM update
    // -------------------------------------------------------------------------

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        WeaponInventoryComponent inventory = INVENTORY_M.get(entity);
        // Only process melee FSM when the melee slot is active
        if (!inventory.isActiveSlotMelee()) return;

        MeleeStateComponent meleeState = MELEE_STATE_M.get(entity);
        MeleeWeaponComponent weapon = MELEE_WEAPON_M.get(entity);
        CombatInputComponent input = COMBAT_INPUT_M.get(entity);
        MovementStateComponent movement = MOVEMENT_M.get(entity);

        switch (meleeState.currentState) {
            case IDLE:
                handleIdle(entity, meleeState, weapon, input, movement, deltaTime);
                break;
            case WIND_UP:
                handleWindUp(meleeState, weapon, movement, deltaTime);
                break;
            case ACTIVE:
                handleActive(entity, meleeState, weapon, input, deltaTime);
                break;
            case RECOVERY:
                handleRecovery(entity, meleeState, weapon, input, movement, deltaTime);
                break;
            case BLOCKING:
                handleBlocking(meleeState, input, deltaTime);
                break;
            case STAGGERED:
                handleStaggered(meleeState, deltaTime);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // FSM state handlers
    // -------------------------------------------------------------------------

    private void handleIdle(Entity entity, MeleeStateComponent meleeState,
                            MeleeWeaponComponent weapon, CombatInputComponent input,
                            MovementStateComponent movement, float deltaTime) {
        if (input.blockHeld) {
            meleeState.currentState = MeleeState.BLOCKING;
            meleeState.blockDirection = input.blockDirection != null
                ? input.blockDirection : AttackDirection.LEFT;
            return;
        }
        if (input.meleeAttackRequested && input.meleeAttackDirection != null) {
            startAttack(entity, meleeState, weapon, input.meleeAttackDirection, movement, 0);
        }
    }

    private void handleWindUp(MeleeStateComponent meleeState, MeleeWeaponComponent weapon,
                              MovementStateComponent movement, float deltaTime) {
        meleeState.stateTimer -= deltaTime;
        if (meleeState.stateTimer <= 0f) {
            // Wind-up finished — enter ACTIVE
            float activeTime = weapon.weightClass.activeTime;
            if (movement != null && movement.isExhausted) {
                DamageConfigData config = getDamageConfig();
                float penalty = config != null ? config.exhaustionAttackPenalty : 0.4f;
                activeTime *= 1f / (1f - penalty);
            }
            meleeState.currentState = MeleeState.ACTIVE;
            meleeState.stateTimer = activeTime;
        }
    }

    private void handleActive(Entity entity, MeleeStateComponent meleeState,
                              MeleeWeaponComponent weapon, CombatInputComponent input,
                              float deltaTime) {
        // Perform hit detection once per ACTIVE phase entry (timer still at full = first frame).
        // We run detection every frame to allow multi-hit if desired, but the caller can gate this.
        performHitDetection(entity, meleeState, weapon);

        meleeState.stateTimer -= deltaTime;
        if (meleeState.stateTimer <= 0f) {
            // Move to RECOVERY
            meleeState.currentState = MeleeState.RECOVERY;
            meleeState.stateTimer = weapon.weightClass.recoveryTime;
            // Combo is allowed during recovery
            meleeState.canCombo = true;
            meleeState.queuedDirection = null;
        }
    }

    private void handleRecovery(Entity entity, MeleeStateComponent meleeState,
                                MeleeWeaponComponent weapon, CombatInputComponent input,
                                MovementStateComponent movement, float deltaTime) {
        // Accept combo input for a *different* direction
        if (meleeState.canCombo && input.meleeAttackRequested && input.meleeAttackDirection != null) {
            if (input.meleeAttackDirection != meleeState.attackDirection) {
                // Queue the different-direction combo
                meleeState.queuedDirection = input.meleeAttackDirection;
            }
            // Same direction: ignore (queuedDirection stays null)
        }

        meleeState.stateTimer -= deltaTime;
        if (meleeState.stateTimer <= 0f) {
            meleeState.canCombo = false;
            if (meleeState.queuedDirection != null) {
                // Execute queued combo
                AttackDirection nextDir = meleeState.queuedDirection;
                meleeState.queuedDirection = null;
                startAttack(entity, meleeState, weapon, nextDir, movement, meleeState.comboCounter);
            } else {
                // Return to IDLE, reset combo
                meleeState.currentState = MeleeState.IDLE;
                meleeState.comboCounter = 0;
                meleeState.stateTimer = 0f;
            }
        }
    }

    private void handleBlocking(MeleeStateComponent meleeState, CombatInputComponent input,
                                float deltaTime) {
        // Update block direction from input
        if (input.blockDirection != null) {
            meleeState.blockDirection = input.blockDirection;
        }
        if (!input.blockHeld) {
            meleeState.currentState = MeleeState.IDLE;
            meleeState.stateTimer = 0f;
        }
    }

    private void handleStaggered(MeleeStateComponent meleeState, float deltaTime) {
        meleeState.stateTimer -= deltaTime;
        if (meleeState.stateTimer <= 0f) {
            meleeState.currentState = MeleeState.IDLE;
            meleeState.stateTimer = 0f;
        }
    }

    // -------------------------------------------------------------------------
    // Attack initiation
    // -------------------------------------------------------------------------

    /**
     * Transitions the entity into WIND_UP, charges stamina cost, and sets up combo state.
     *
     * @param prevComboCounter combo hits completed so far (0 for a fresh attack)
     */
    private void startAttack(Entity entity, MeleeStateComponent meleeState,
                             MeleeWeaponComponent weapon, AttackDirection direction,
                             MovementStateComponent movement, int prevComboCounter) {
        DamageConfigData config = getDamageConfig();

        // Stamina cost (scales with combo depth)
        float baseCost = weapon.staminaCosts.getOrDefault(direction, 15f);
        int newComboCounter = prevComboCounter + 1;
        float staminaPenalty = config != null ? config.comboStaminaPenalty : 0.2f;
        float cost = baseCost * (1f + staminaPenalty * (newComboCounter - 1));

        if (movement != null) {
            movement.currentStamina = Math.max(0f, movement.currentStamina - cost);
        }

        // Compute wind-up duration (exhaustion extends it)
        float windUpTime = weapon.weightClass.windUpTime;
        if (movement != null && movement.isExhausted) {
            float penalty = config != null ? config.exhaustionAttackPenalty : 0.4f;
            windUpTime *= 1f / (1f - penalty);
        }

        meleeState.currentState = MeleeState.WIND_UP;
        meleeState.attackDirection = direction;
        meleeState.stateTimer = windUpTime;
        meleeState.comboCounter = newComboCounter;
        meleeState.canCombo = false;
        meleeState.queuedDirection = null;
        meleeState.hitThisSwing.clear();
    }

    // -------------------------------------------------------------------------
    // Hit detection
    // -------------------------------------------------------------------------

    private void performHitDetection(Entity attacker, MeleeStateComponent meleeState,
                                     MeleeWeaponComponent weapon) {
        if (engine == null) return;

        TransformComponent attackerTransform = TRANSFORM_M.get(attacker);
        if (attackerTransform == null) return;

        DamageConfigData config = getDamageConfig();

        // Compute attacker forward vector from aim direction stored in CombatInputComponent
        CombatInputComponent input = COMBAT_INPUT_M.get(attacker);
        attackerForward.set(input.aimDirection).nor();
        if (attackerForward.isZero()) {
            attackerForward.set(0f, 0f, -1f); // default forward
        }

        ImmutableArray<Entity> targets = engine.getEntitiesFor(TARGET_FAMILY);
        for (Entity target : targets) {
            if (target == attacker) continue;
            if (meleeState.hitThisSwing.contains(target)) continue;

            HealthComponent hp = HEALTH_M.get(target);
            if (hp == null || !hp.alive) continue;

            TransformComponent targetTransform = TRANSFORM_M.get(target);
            if (targetTransform == null) continue;

            // Range check
            toTarget.set(targetTransform.position).sub(attackerTransform.position);
            float distance = toTarget.len();
            if (distance > weapon.reach) continue;

            // Facing check
            float dot = toTarget.nor().dot(attackerForward);
            if (dot < FACING_DOT_THRESHOLD) continue;

            // Determine hit region from height difference
            HitboxComponent hitbox = HITBOX_M.get(target);
            HitRegion hitRegion = determineHitRegion(attackerTransform, targetTransform, hitbox);

            // Check if target is blocking
            MeleeStateComponent targetMeleeState = MELEE_STATE_M.get(target);
            if (targetMeleeState != null && targetMeleeState.currentState == MeleeState.BLOCKING) {
                handleBlockedHit(attacker, target, meleeState, targetMeleeState, weapon, config);
            } else {
                // Unblocked hit — compute damage and publish
                float damage = computeDamage(weapon, meleeState, config);
                eventBus.publish(new MeleeHitEvent(
                    attacker, target,
                    meleeState.attackDirection,
                    hitRegion,
                    damage,
                    weapon.damageType
                ));
            }
            meleeState.hitThisSwing.add(target);
        }
    }

    /**
     * Handles the case where a target is actively blocking.
     *
     * <ul>
     *   <li>Perfect block (same direction) → attacker is STAGGERED, {@link MeleeBlockEvent}
     *       published with {@code perfectBlock=true}, no damage.</li>
     *   <li>Wrong block → reduced damage {@link MeleeHitEvent} and {@link MeleeBlockEvent}
     *       with {@code perfectBlock=false}.</li>
     * </ul>
     */
    private void handleBlockedHit(Entity attacker, Entity target,
                                  MeleeStateComponent attackerState,
                                  MeleeStateComponent targetState,
                                  MeleeWeaponComponent weapon,
                                  DamageConfigData config) {
        boolean perfectBlock = (targetState.blockDirection == attackerState.attackDirection);

        eventBus.publish(new MeleeBlockEvent(
            attacker, target,
            attackerState.attackDirection,
            targetState.blockDirection,
            perfectBlock
        ));

        if (perfectBlock) {
            // Stagger the attacker
            float staggerTime = weapon.weightClass.staggerTime;
            attackerState.currentState = MeleeState.STAGGERED;
            attackerState.stateTimer = staggerTime;
            attackerState.canCombo = false;
            attackerState.queuedDirection = null;
        } else {
            // Wrong block: reduced damage still gets through
            TransformComponent attackerTransform = TRANSFORM_M.get(attacker);
            TransformComponent targetTransform = TRANSFORM_M.get(target);
            HitboxComponent hitbox = HITBOX_M.get(target);
            HitRegion hitRegion = determineHitRegion(attackerTransform, targetTransform, hitbox);

            float mitigation = config != null ? config.wrongBlockMitigation : 0.3f;
            float damage = computeDamage(weapon, attackerState, config) * mitigation;
            eventBus.publish(new MeleeHitEvent(
                attacker, target,
                attackerState.attackDirection,
                hitRegion,
                damage,
                weapon.damageType
            ));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Computes final damage including directional modifier and combo bonus.
     */
    private float computeDamage(MeleeWeaponComponent weapon, MeleeStateComponent meleeState,
                                DamageConfigData config) {
        float dirMod = weapon.directionalModifiers.getOrDefault(meleeState.attackDirection, 1.0f);
        float damage = weapon.baseDamage * dirMod;

        // Combo bonus (applied from the 2nd hit onward, counter starts at 1 for first hit)
        int comboIndex = Math.max(0, meleeState.comboCounter - 1);
        float comboBonus = config != null ? config.comboDamageBonus : 0.1f;
        damage *= 1f + (comboBonus * comboIndex);

        return damage;
    }

    /**
     * Determines the hit region based on the vertical offset between attacker and target.
     * Falls back to TORSO when hitbox data is unavailable.
     */
    private HitRegion determineHitRegion(TransformComponent attackerTransform,
                                         TransformComponent targetTransform,
                                         HitboxComponent hitbox) {
        if (hitbox == null) return HitRegion.TORSO;

        float targetBottom = targetTransform.position.y - hitbox.bodyHeight * 0.5f;
        float attackerMidY = attackerTransform.position.y;
        float hitHeightRatio = (attackerMidY - targetBottom) / hitbox.bodyHeight;
        hitHeightRatio = Math.max(0f, Math.min(1f, hitHeightRatio));
        return hitbox.getRegionForHeight(hitHeightRatio);
    }

    private DamageConfigData getDamageConfig() {
        return combatData != null ? combatData.getDamageConfig() : null;
    }
}
