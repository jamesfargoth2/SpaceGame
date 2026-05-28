package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingInitiationSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingInitiationSystemTest {

    private Entity shipAt(Engine engine, float x, boolean vulnerable) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
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
    void findsNearestVulnerableShipInRange() {
        Engine engine = new Engine();
        Entity self = shipAt(engine, 0, false);
        Entity near = shipAt(engine, 30, true);
        shipAt(engine, 500, true); // out of range

        Entity found = BoardingInitiationSystem.nearestBoardable(
            engine, self, new Vector3(0, 0, 0), 100f);
        assertSame(near, found);
    }

    @Test
    void ignoresHealthyAndSelf() {
        Engine engine = new Engine();
        Entity self = shipAt(engine, 0, true); // self is VULNERABLE but must be excluded
        shipAt(engine, 20, false);             // healthy
        assertNull(BoardingInitiationSystem.nearestBoardable(
            engine, self, new Vector3(0, 0, 0), 100f));
    }

    @Test
    void pressingBoardLaunchesPodAtNearbyVulnerableShip() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        BoardingAttachSystem attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);

        Entity self = shipAt(engine, 0, false);
        Entity target = shipAt(engine, 30, true);

        // Player piloting `self`, pressing G.
        Entity player = com.galacticodyssey.ship.boarding.BoardingTestSupport
            .pilotingPlayer(engine, self, /*boardPressed*/ true);

        BoardingInitiationSystem init = new BoardingInitiationSystem(eventBus, attach);
        engine.addSystem(init);
        engine.update(0.016f);

        assertEquals(1, engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(BreachingPodComponent.class).get()).size(),
            "G near a VULNERABLE ship launches a breaching pod");
        assertFalse(player.getComponent(
            com.galacticodyssey.player.components.PlayerInputComponent.class).boardPressed,
            "board input consumed");
    }
}
