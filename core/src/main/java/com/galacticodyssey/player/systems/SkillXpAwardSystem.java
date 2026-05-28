package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ResourceCollectedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.stealth.AwarenessState;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.water.events.HullRepairEvent;

/**
 * Centralizes XP awards: subscribes to gameplay events and forwards to
 * {@link RealTimeSkillSystem}. Keeps gameplay systems unaware of progression
 * (architectural rule #3). Only player-sourced actions award XP.
 *
 * <p>Accrual-based skills (Athletics, Piloting) are handled in {@link #update}.</p>
 *
 * <p>TODO hook: Repair currently maps only to {@link HullRepairEvent} (water hull).
 * Ship-module repair XP awaits a dedicated repair-complete event.</p>
 */
public class SkillXpAwardSystem extends EntitySystem {

    public static final int PRIORITY = 27;

    private static final ComponentMapper<MovementStateComponent> MOVE_M =
        ComponentMapper.getFor(MovementStateComponent.class);
    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private static final float ATHLETICS_DISTANCE_PER_XP = 10f;
    private static final float PILOTING_XP_PER_SECOND     = 2f;

    private float sprintDistanceAccum;

    private final EventBus eventBus;
    private final RealTimeSkillSystem skillSystem;
    private final ImmutableArray<Entity> players;

    public SkillXpAwardSystem(EventBus eventBus, RealTimeSkillSystem skillSystem, Engine engine) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.skillSystem = skillSystem;
        this.players = engine.getEntitiesFor(Family.all(PlayerStatsComponent.class).get());
        subscribe();
    }

    private Entity player() {
        return players.size() > 0 ? players.first() : null;
    }

    private boolean isPlayer(Entity e) {
        Entity p = player();
        return p != null && p == e;
    }

    private void subscribe() {
        eventBus.subscribe(DamageDealtEvent.class, e -> {
            if (!isPlayer(e.attacker)) return;
            RealTimeSkill skill = weaponSkill(e.damageType);
            if (skill != null) skillSystem.awardSkillXP(player(), skill, e.finalDamage * 0.1f, 1f);
        });
        eventBus.subscribe(EntityKilledEvent.class, e -> {
            if (!isPlayer(e.killer)) return;
            skillSystem.awardSkillXP(player(), RealTimeSkill.FIREARMS, 15f, 1f);
        });
        eventBus.subscribe(MeleeHitEvent.class, e -> {
            if (!isPlayer(e.attacker)) return;
            skillSystem.awardSkillXP(player(), RealTimeSkill.MELEE, e.damage * 0.15f, 1f);
        });
        eventBus.subscribe(ResourceCollectedEvent.class, e ->
            awardIfPlayer(RealTimeSkill.MINING, e.amount * 2f));
        eventBus.subscribe(TradeCompletedEvent.class, e ->
            awardIfPlayer(RealTimeSkill.TRADING, e.totalPrice * 0.01f));
        eventBus.subscribe(HullRepairEvent.class, e -> {
            if (!isPlayer(e.player)) return;
            skillSystem.awardSkillXP(player(), RealTimeSkill.REPAIR, 10f, 1f);
        });
        eventBus.subscribe(AwarenessChangedEvent.class, e -> {
            if (e.newState == AwarenessState.UNAWARE && e.oldState != AwarenessState.UNAWARE) {
                awardIfPlayer(RealTimeSkill.STEALTH, 20f);
            }
        });
    }

    /** For single-player events with no source entity, the player is the actor. */
    private void awardIfPlayer(RealTimeSkill skill, float baseXP) {
        Entity p = player();
        if (p != null) skillSystem.awardSkillXP(p, skill, baseXP, 1f);
    }

    private static RealTimeSkill weaponSkill(DamageType type) {
        switch (type) {
            case BALLISTIC: return RealTimeSkill.FIREARMS;
            case ENERGY:
            case PLASMA:    return RealTimeSkill.ENERGY_WEAPONS;
            default:        return null;
        }
    }

    @Override
    public void update(float deltaTime) {
        Entity p = player();
        if (p == null) return;

        MovementStateComponent move = MOVE_M.get(p);
        if (move != null && move.isSprinting && move.isGrounded && move.currentSpeed > 0f) {
            sprintDistanceAccum += move.currentSpeed * deltaTime;
            if (sprintDistanceAccum >= ATHLETICS_DISTANCE_PER_XP) {
                int chunks = (int) (sprintDistanceAccum / ATHLETICS_DISTANCE_PER_XP);
                sprintDistanceAccum -= chunks * ATHLETICS_DISTANCE_PER_XP;
                skillSystem.awardSkillXP(p, RealTimeSkill.ATHLETICS, chunks, 1f);
            }
        }

        PlayerStateComponent state = STATE_M.get(p);
        if (state != null && state.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            skillSystem.awardSkillXP(p, RealTimeSkill.PILOTING, PILOTING_XP_PER_SECOND * deltaTime, 1f);
        }
    }
}
