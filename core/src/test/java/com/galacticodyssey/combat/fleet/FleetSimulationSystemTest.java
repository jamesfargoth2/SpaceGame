package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.FleetBattleResolvedEvent;
import com.galacticodyssey.combat.fleet.systems.FleetSimulationSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FleetSimulationSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new FleetSimulationSystem(eventBus));
    }

    private Entity createFleet(String id, String factionId, FleetDoctrine doctrine,
                                FleetShipClass shipClass, int count) {
        Entity e = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = id;
        fc.factionId = factionId;
        fc.doctrine = doctrine;
        fc.state = FleetState.ENGAGED;
        fc.expanded = false;
        fc.composition.add(new FleetShipEntry(shipClass, count, 1.0f));
        fc.recomputeAggregates();
        e.add(fc);

        FleetTacticsComponent ftc = new FleetTacticsComponent();
        ftc.threatAssessment.put(id.equals("attacker") ? "defender" : "attacker", 1.0f);
        e.add(ftc);

        FleetFormationComponent ffc = new FleetFormationComponent();
        e.add(ffc);
        return e;
    }

    @Test
    void strongerFleetWinsBattle() {
        Entity attacker = createFleet("attacker", "faction-a", FleetDoctrine.AGGRESSIVE,
            FleetShipClass.CRUISER, 10);
        Entity defender = createFleet("defender", "faction-b", FleetDoctrine.DEFENSIVE,
            FleetShipClass.FIGHTER, 5);
        engine.addEntity(attacker);
        engine.addEntity(defender);

        AtomicReference<FleetBattleResolvedEvent> result = new AtomicReference<>();
        eventBus.subscribe(FleetBattleResolvedEvent.class, result::set);

        for (int i = 0; i < 100; i++) {
            engine.update(5.0f);
            if (result.get() != null) break;
        }

        assertNotNull(result.get(), "Battle should resolve");
        assertEquals("attacker", result.get().winnerFleetId);
    }

    @Test
    void fleetRetreatsAtThreshold() {
        Entity strong = createFleet("attacker", "faction-a", FleetDoctrine.AGGRESSIVE,
            FleetShipClass.BATTLESHIP, 5);
        Entity weak = createFleet("defender", "faction-b", FleetDoctrine.DEFENSIVE,
            FleetShipClass.CORVETTE, 8);
        engine.addEntity(strong);
        engine.addEntity(weak);

        AtomicReference<FleetBattleResolvedEvent> result = new AtomicReference<>();
        eventBus.subscribe(FleetBattleResolvedEvent.class, result::set);

        for (int i = 0; i < 100; i++) {
            engine.update(5.0f);
            if (result.get() != null) break;
        }

        assertNotNull(result.get());
        assertEquals("defender", result.get().loserFleetId);
    }
}
