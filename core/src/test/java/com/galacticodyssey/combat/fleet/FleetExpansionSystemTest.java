package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetExpansionSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetExpansionSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Vector3 playerPos;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        playerPos = new Vector3(0f, 0f, 0f);
        FormationRegistry formationRegistry = new FormationRegistry();
        formationRegistry.registerDefaults(50);
        engine.addSystem(new FleetExpansionSystem(eventBus, () -> playerPos, formationRegistry));
    }

    @Test
    void expandsFleetWhenPlayerIsNearby() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.factionId = "faction-a";
        fc.expanded = false;
        fc.state = FleetState.PATROL;
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 3, 1.0f));
        fc.recomputeAggregates();
        fleet.add(fc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        ffc.localAnchorX = 100f;
        fleet.add(ffc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        engine.addEntity(fleet);

        playerPos.set(500f, 0f, 0f);
        engine.update(1.0f);

        assertTrue(fc.expanded);
        int shipCount = engine.getEntitiesFor(
            Family.all(FleetMemberComponent.class).get()).size();
        assertEquals(3, shipCount);
    }

    @Test
    void collapsesFleetWhenPlayerIsFar() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-2";
        fc.factionId = "faction-a";
        fc.expanded = true;
        fc.state = FleetState.PATROL;
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 2, 1.0f));
        fc.recomputeAggregates();
        fleet.add(fc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        ffc.localAnchorX = 0f;
        fleet.add(ffc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        engine.addEntity(fleet);

        Entity ship1 = createShipFor("fleet-2", fleet);
        Entity ship2 = createShipFor("fleet-2", fleet);
        engine.addEntity(ship1);
        engine.addEntity(ship2);

        playerPos.set(100000f, 0f, 0f);
        engine.update(1.0f);

        assertFalse(fc.expanded);
        int shipCount = engine.getEntitiesFor(
            Family.all(FleetMemberComponent.class).get()).size();
        assertEquals(0, shipCount);
    }

    private Entity createShipFor(String fleetId, Entity fleetEntity) {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetId = fleetId;
        fmc.fleetEntity = fleetEntity;
        ship.add(fmc);
        return ship;
    }
}
