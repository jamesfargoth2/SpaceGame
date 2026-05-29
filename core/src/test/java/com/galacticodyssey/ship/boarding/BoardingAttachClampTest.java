package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.components.ShipEntryPointComponent;
import com.galacticodyssey.ship.docking.events.DockingCaptureEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingAttachClampTest {

    private EventBus eventBus;
    private Engine engine;
    private final List<ShipBreachedEvent> breaches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingAttachSystem(eventBus));
        eventBus.subscribe(ShipBreachedEvent.class, breaches::add);
    }

    private Entity ship(boolean vulnerable) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        ShipEntryPointComponent entry = new ShipEntryPointComponent();
        entry.interiorPosition.set(1f, 2f, 3f);
        e.add(entry);
        if (vulnerable) {
            BoardingOperationComponent op = new BoardingOperationComponent();
            op.targetShip = e;
            op.phase = BoardingPhase.VULNERABLE;
            e.add(op);
        }
        engine.addEntity(e);
        return e;
    }

    @Test
    void clampOnVulnerableTargetBreachesViaAirlock() {
        Entity aggressor = ship(false);
        Entity target = ship(true);

        eventBus.publish(new DockingCaptureEvent(aggressor, target));
        engine.update(0.016f);

        BoardingOperationComponent op = target.getComponent(BoardingOperationComponent.class);
        assertEquals(BoardingPhase.BREACHED, op.phase);
        assertEquals(AttachMethod.CLAMP, op.attachMethod);
        assertEquals(new Vector3(1f, 2f, 3f), op.entryLocalPosition);
        assertSame(aggressor, op.aggressorShip);
        assertTrue(op.playerIsAggressor,
            "a clamp bridge on a still-VULNERABLE target is always the player (per design)");
        assertEquals(1, breaches.size());
        assertSame(target, breaches.get(0).target);
    }

    @Test
    void clampBetweenTwoHealthyShipsDoesNothing() {
        Entity a = ship(false);
        Entity b = ship(false);
        eventBus.publish(new DockingCaptureEvent(a, b));
        engine.update(0.016f);
        assertTrue(breaches.isEmpty());
        assertNull(a.getComponent(BoardingOperationComponent.class));
        assertNull(b.getComponent(BoardingOperationComponent.class));
    }
}
