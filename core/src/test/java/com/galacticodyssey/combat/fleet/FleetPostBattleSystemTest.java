package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetPostBattleSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetPostBattleSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new FleetPostBattleSystem(eventBus));
    }

    @Test
    void autoRepairRestoresHullOverTime() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.expanded = true;
        fc.state = FleetState.REGROUPING;
        fleet.add(fc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleet.add(ffc);
        engine.addEntity(fleet);

        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetId = "fleet-1";
        fmc.fleetEntity = fleet;
        ship.add(fmc);
        HealthComponent hp = new HealthComponent();
        hp.maxHP = 1000f;
        hp.currentHP = 500f;
        ship.add(hp);
        engine.addEntity(ship);

        for (int i = 0; i < 60; i++) {
            engine.update(1.0f);
        }

        assertTrue(hp.currentHP > 500f, "HP should have increased from auto-repair");
        assertTrue(hp.currentHP <= hp.maxHP, "HP should not exceed max");
    }

    @Test
    void deadShipsAreNotRepaired() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-2";
        fc.expanded = true;
        fc.state = FleetState.REGROUPING;
        fleet.add(fc);
        fleet.add(new FleetTacticsComponent());
        fleet.add(new FleetFormationComponent());
        engine.addEntity(fleet);

        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetId = "fleet-2";
        fmc.fleetEntity = fleet;
        ship.add(fmc);
        HealthComponent hp = new HealthComponent();
        hp.maxHP = 1000f;
        hp.currentHP = 0f;
        hp.alive = false;
        ship.add(hp);
        engine.addEntity(ship);

        for (int i = 0; i < 60; i++) {
            engine.update(1.0f);
        }

        assertEquals(0f, hp.currentHP, "Dead ships should not be repaired");
        assertFalse(hp.alive, "Dead ships should remain dead");
    }
}
