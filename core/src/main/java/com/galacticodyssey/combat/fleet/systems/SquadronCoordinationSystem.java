package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.utils.IntMap;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups ship entities by fleet+squadron index and coordinates focus fire across
 * each squadron. On every {@value #TICK_INTERVAL}-second tick, the highest-threat
 * target held by any member is broadcast to all squadronmates that have no current
 * target, concentrating fire on one enemy at a time.
 *
 * <p>Priority {@value #PRIORITY} — runs after {@link FleetCommandSystem} (5) so that
 * any fleet-state changes this tick are already applied.
 */
public class SquadronCoordinationSystem extends EntitySystem {

    public static final int PRIORITY = 6;
    private static final float TICK_INTERVAL = 0.5f;

    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, CombatAIComponent.class,
        HealthComponent.class, TransformComponent.class
    ).get();

    private final EventBus eventBus;
    private final IntMap<List<Entity>> squadronGroups = new IntMap<>();
    private float accumulator;
    private Engine engine;

    public SquadronCoordinationSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

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

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        squadronGroups.clear();
        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            HealthComponent hp = HEALTH_M.get(e);
            if (hp == null || !hp.alive) continue;

            int key = computeGroupKey(fmc);
            List<Entity> group = squadronGroups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                squadronGroups.put(key, group);
            }
            group.add(e);
        }

        for (IntMap.Entry<List<Entity>> entry : squadronGroups) {
            coordinateSquadron(entry.value);
        }
    }

    private int computeGroupKey(FleetMemberComponent fmc) {
        return (fmc.fleetId != null ? fmc.fleetId.hashCode() : 0) * 31 + fmc.squadronIndex;
    }

    private void coordinateSquadron(List<Entity> members) {
        Entity bestTarget = null;
        float highestThreat = -1f;

        for (Entity member : members) {
            CombatAIComponent ai = AI_M.get(member);
            if (ai.currentTarget != null) {
                HealthComponent targetHp = HEALTH_M.get(ai.currentTarget);
                if (targetHp != null && targetHp.alive && ai.threatLevel > highestThreat) {
                    bestTarget = ai.currentTarget;
                    highestThreat = ai.threatLevel;
                }
            }
        }

        if (bestTarget != null) {
            for (Entity member : members) {
                CombatAIComponent ai = AI_M.get(member);
                if (ai.currentTarget == null) {
                    ai.currentTarget = bestTarget;
                }
            }
        }
    }
}
