package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.core.EventBus;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves off-screen fleet battles every {@value #TICK_INTERVAL} seconds.
 *
 * <p>On each tick, all pairs of {@link FleetState#ENGAGED} collapsed (non-expanded) fleets
 * that have each other in their {@link FleetTacticsComponent#threatAssessment} maps exchange
 * fire. Damage is applied via {@link #applyDamage} and casualties are removed from
 * {@link FleetComponent#composition}. When a fleet's {@link FleetComponent#lossRatio()} meets
 * or exceeds its {@link FleetTacticsComponent#retreatThreshold} it sets state to
 * {@link FleetState#RETREATING} and a {@link FleetBattleResolvedEvent} is published.
 *
 * <p>Priority {@value #PRIORITY} — runs before formation/command systems.
 */
public class FleetSimulationSystem extends EntitySystem {

    public static final int PRIORITY = 2;
    private static final float TICK_INTERVAL = 5.0f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetTacticsComponent> TACTICS_M =
        ComponentMapper.getFor(FleetTacticsComponent.class);

    private static final Family FLEET_FAMILY = Family.all(
        FleetComponent.class, FleetTacticsComponent.class, FleetFormationComponent.class
    ).get();

    private final EventBus eventBus;
    private float accumulator;
    private Engine engine;

    public FleetSimulationSystem(EventBus eventBus) {
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

        List<Entity> engagedFleets = new ArrayList<>();
        for (Entity e : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(e);
            if (fc.state == FleetState.ENGAGED && !fc.expanded) {
                engagedFleets.add(e);
            }
        }

        for (int i = 0; i < engagedFleets.size(); i++) {
            Entity a = engagedFleets.get(i);
            FleetComponent fcA = FLEET_M.get(a);
            FleetTacticsComponent tcA = TACTICS_M.get(a);

            for (int j = i + 1; j < engagedFleets.size(); j++) {
                Entity b = engagedFleets.get(j);
                FleetComponent fcB = FLEET_M.get(b);
                FleetTacticsComponent tcB = TACTICS_M.get(b);

                if (!tcA.threatAssessment.containsKey(fcB.fleetId) &&
                    !tcB.threatAssessment.containsKey(fcA.fleetId)) {
                    continue;
                }

                resolveRound(a, fcA, tcA, b, fcB, tcB);
            }
        }
    }

    private void resolveRound(Entity a, FleetComponent fcA, FleetTacticsComponent tcA,
                              Entity b, FleetComponent fcB, FleetTacticsComponent tcB) {
        float damageToB = fcA.aggregateFirepower * fcA.doctrine.damageDealtModifier
                        * fcB.doctrine.damageTakenModifier;
        float damageToA = fcB.aggregateFirepower * fcB.doctrine.damageDealtModifier
                        * fcA.doctrine.damageTakenModifier;

        int casualtiesA = applyDamage(fcA, damageToA);
        int casualtiesB = applyDamage(fcB, damageToB);
        fcA.recomputeAggregates();
        fcB.recomputeAggregates();

        boolean aRetreats = fcA.lossRatio() >= fcA.doctrine.retreatThreshold;
        boolean bRetreats = fcB.lossRatio() >= fcB.doctrine.retreatThreshold;

        if (aRetreats || bRetreats) {
            String winnerId, loserId;
            int winnerCasualties, loserCasualties;
            if (aRetreats && !bRetreats) {
                winnerId = fcB.fleetId; loserId = fcA.fleetId;
                winnerCasualties = casualtiesB; loserCasualties = casualtiesA;
                fcA.state = FleetState.RETREATING;
                fcB.state = FleetState.PATROL;
            } else if (bRetreats && !aRetreats) {
                winnerId = fcA.fleetId; loserId = fcB.fleetId;
                winnerCasualties = casualtiesA; loserCasualties = casualtiesB;
                fcB.state = FleetState.RETREATING;
                fcA.state = FleetState.PATROL;
            } else {
                winnerId = fcA.aggregateFirepower >= fcB.aggregateFirepower ? fcA.fleetId : fcB.fleetId;
                loserId = winnerId.equals(fcA.fleetId) ? fcB.fleetId : fcA.fleetId;
                winnerCasualties = winnerId.equals(fcA.fleetId) ? casualtiesA : casualtiesB;
                loserCasualties = winnerId.equals(fcA.fleetId) ? casualtiesB : casualtiesA;
                fcA.state = FleetState.RETREATING;
                fcB.state = FleetState.RETREATING;
            }
            eventBus.publish(new FleetBattleResolvedEvent(
                winnerId, loserId, winnerCasualties, loserCasualties, List.of()));
        }
    }

    private int applyDamage(FleetComponent fc, float damage) {
        int totalCasualties = 0;
        float remaining = damage;
        for (int i = 0; i < fc.composition.size() && remaining > 0; i++) {
            FleetShipEntry entry = fc.composition.get(i);
            float hpPerShip = entry.shipClass.baseHullHp * entry.avgHpRatio;
            if (hpPerShip <= 0) continue;
            int killed = Math.min(entry.count, (int) (remaining / hpPerShip));
            if (killed > 0) {
                entry.count -= killed;
                remaining -= killed * hpPerShip;
                totalCasualties += killed;
            }
            if (remaining > 0 && entry.count > 0) {
                float hpLost = remaining / (entry.count * entry.shipClass.baseHullHp);
                entry.avgHpRatio = Math.max(0.1f, entry.avgHpRatio - hpLost);
                remaining = 0;
            }
        }
        fc.composition.removeIf(e -> e.count <= 0);
        return totalCasualties;
    }
}
