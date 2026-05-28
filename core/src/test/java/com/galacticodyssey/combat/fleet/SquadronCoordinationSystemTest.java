package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.SquadronCoordinationSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SquadronCoordinationSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity fleetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new SquadronCoordinationSystem(eventBus));

        fleetEntity = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "test-fleet";
        fc.expanded = true;
        fc.state = FleetState.ENGAGED;
        fleetEntity.add(fc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleetEntity.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleetEntity.add(ffc);
        engine.addEntity(fleetEntity);
    }

    private Entity createShip(int squadronIndex) {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetEntity = fleetEntity;
        fmc.fleetId = "test-fleet";
        fmc.squadronIndex = squadronIndex;
        ship.add(fmc);
        CombatAIComponent ai = new CombatAIComponent();
        ship.add(ai);
        HealthComponent hp = new HealthComponent();
        ship.add(hp);
        TransformComponent tc = new TransformComponent();
        ship.add(tc);
        return ship;
    }

    @Test
    void squadronMembersShareTarget() {
        Entity ship1 = createShip(0);
        Entity ship2 = createShip(0);
        Entity enemy = new Entity();
        HealthComponent enemyHp = new HealthComponent();
        enemy.add(enemyHp);
        TransformComponent enemyTc = new TransformComponent();
        enemy.add(enemyTc);

        CombatAIComponent ai1 = ship1.getComponent(CombatAIComponent.class);
        ai1.currentTarget = enemy;

        engine.addEntity(ship1);
        engine.addEntity(ship2);
        engine.addEntity(enemy);

        engine.update(0.5f);

        CombatAIComponent ai2 = ship2.getComponent(CombatAIComponent.class);
        assertEquals(enemy, ai2.currentTarget);
    }
}
