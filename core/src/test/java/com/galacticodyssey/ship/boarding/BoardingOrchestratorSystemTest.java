package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipBoardableEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingOrchestratorSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingOrchestratorSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity ship;
    private final List<ShipBoardableEvent> boardable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingOrchestratorSystem(eventBus));
        ship = new Entity();
        engine.addEntity(ship);
        eventBus.subscribe(ShipBoardableEvent.class, boardable::add);
    }

    @Test
    void enginesDisabledLazilyAddsOperationAndGoesVulnerable() {
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.ENGINES));
        engine.update(0.016f);

        BoardingOperationComponent op = ship.getComponent(BoardingOperationComponent.class);
        assertNotNull(op, "operation component added lazily on engine disable");
        assertEquals(BoardingPhase.VULNERABLE, op.phase);
        assertEquals(1, boardable.size());
        assertSame(ship, boardable.get(0).ship);
    }

    @Test
    void nonEngineSubsystemDoesNotMakeBoardable() {
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.WEAPONS));
        engine.update(0.016f);
        assertNull(ship.getComponent(BoardingOperationComponent.class));
        assertTrue(boardable.isEmpty());
    }

    @Test
    void alreadyVulnerableDoesNotRepublish() {
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.ENGINES));
        engine.update(0.016f);
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.ENGINES));
        engine.update(0.016f);
        assertEquals(1, boardable.size(), "VULNERABLE is entered only once");
    }
}
