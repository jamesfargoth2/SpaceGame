package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
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
 * <p>Accrual-based skills (Athletics, Piloting) are handled in {@link #update}
 * (added in a later task).</p>
 *
 * <p>TODO hook: Repair currently maps only to {@link HullRepairEvent} (water hull).
 * Ship-module repair XP awaits a dedicated repair-complete event.</p>
 */
public class SkillXpAwardSystem extends EntitySystem {

    public static final int PRIORITY = 27;

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
        // Accrual hooks (Athletics, Piloting) added in a later task.
    }
}
