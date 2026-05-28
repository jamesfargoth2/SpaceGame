package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.combat.fleet.systems.FleetCommandSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetCommandSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new FleetCommandSystem(eventBus));
    }

    @Test
    void orderEventEnqueuesOnFleet() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.expanded = true;
        fc.state = FleetState.ENGAGED;
        fleet.add(fc);

        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleet.add(ffc);
        engine.addEntity(fleet);

        FleetOrder order = FleetOrder.retreat();
        eventBus.publish(new FleetOrderEvent("fleet-1", order));

        engine.update(1.0f);

        assertEquals(FleetState.RETREATING, fc.state);
    }

    @Test
    void doctrineChangePublishesEvent() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.doctrine = FleetDoctrine.BALANCED;
        fc.expanded = true;
        fleet.add(fc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleet.add(ffc);
        engine.addEntity(fleet);

        FleetOrder order = new FleetOrder(FleetOrderType.SET_FORMATION, null, 0, 0, 0, "line", null);
        eventBus.publish(new FleetOrderEvent("fleet-1", order));

        engine.update(1.0f);

        assertEquals("line", ffc.formationTemplateId);
    }
}
