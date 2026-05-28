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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingAttachPodTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;
    private final List<ShipBreachedEvent> breaches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        eventBus.subscribe(ShipBreachedEvent.class, breaches::add);
    }

    private Entity shipAt(float x, float y, float z, boolean vulnerable) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, y, z);
        e.add(t);
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
    void launchPodRequiresVulnerableTarget() {
        Entity aggressor = shipAt(0, 0, 0, false);
        Entity healthy = shipAt(10, 0, 0, false);
        assertNull(attach.launchPod(aggressor, healthy), "cannot board a non-VULNERABLE ship");
    }

    @Test
    void podFliesThenBreachesAtImpactLocalPosition() {
        Entity aggressor = shipAt(0, 0, 0, false);
        Entity target = shipAt(20, 0, 0, true);

        Entity pod = attach.launchPod(aggressor, target);
        assertNotNull(pod);
        assertEquals(BoardingPhase.ATTACHING, target.getComponent(BoardingOperationComponent.class).phase);

        // flightDuration default 1.5s — step until past it.
        for (int i = 0; i < 120 && breaches.isEmpty(); i++) {
            engine.update(0.05f);
        }

        BoardingOperationComponent op = target.getComponent(BoardingOperationComponent.class);
        assertEquals(BoardingPhase.BREACHED, op.phase);
        assertEquals(AttachMethod.BREACH_POD, op.attachMethod);
        // impactPoint == target world position (20,0,0); local = impact - targetPos == (0,0,0).
        assertEquals(new Vector3(0, 0, 0), op.entryLocalPosition);
        assertEquals(1, breaches.size());
        assertEquals(0, engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(BreachingPodComponent.class).get()).size(),
            "pod consumed on impact");
    }
}
