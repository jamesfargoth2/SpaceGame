package com.galacticodyssey.combat.fleet.ai;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.core.EventBus;

public final class AdmiralBehaviorTree {

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetTacticsComponent> TACTICS_M =
        ComponentMapper.getFor(FleetTacticsComponent.class);
    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    private static final Family MEMBER_HEALTH_FAMILY = Family.all(
        FleetMemberComponent.class, HealthComponent.class
    ).get();

    private AdmiralBehaviorTree() {}

    public static void evaluate(Entity fleetEntity, Engine engine, EventBus eventBus) {
        FleetComponent fc = FLEET_M.get(fleetEntity);
        FleetTacticsComponent tc = TACTICS_M.get(fleetEntity);
        if (fc == null || tc == null) return;
        if (fc.state == FleetState.RETREATING || fc.state == FleetState.REGROUPING) return;

        float lossRatio = computeLossRatio(fc, engine);
        float retreatThreshold = fc.doctrine.retreatThreshold;
        if (lossRatio >= retreatThreshold) {
            tc.orders.add(FleetOrder.retreat());
            return;
        }

        float enemyStrength = computeEnemyStrength(tc);
        float strengthRatio = enemyStrength > 0 ? fc.aggregateFirepower / enemyStrength : 10f;

        if (strengthRatio >= fc.doctrine.engageStrengthRatio) {
            if (fc.state != FleetState.ENGAGED) {
                tc.orders.add(FleetOrder.attackTarget(null, new int[0]));
            }
        }
    }

    private static float computeLossRatio(FleetComponent fc, Engine engine) {
        int alive = 0, total = 0;
        for (Entity e : engine.getEntitiesFor(MEMBER_HEALTH_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            if (!fc.fleetId.equals(fmc.fleetId)) continue;
            total++;
            HealthComponent hp = HEALTH_M.get(e);
            if (hp.alive) alive++;
        }
        if (total == 0) return 1f;
        return 1f - ((float) alive / total);
    }

    private static float computeEnemyStrength(FleetTacticsComponent tc) {
        float total = 0f;
        for (float threat : tc.threatAssessment.values()) {
            total += threat;
        }
        return total;
    }
}
