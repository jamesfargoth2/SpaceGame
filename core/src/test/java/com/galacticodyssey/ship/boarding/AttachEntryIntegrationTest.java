package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingEntrySystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttachEntryIntegrationTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        engine.addSystem(new BoardingEntrySystem(eventBus, null));
    }

    @Test
    void podBoardingPutsPlayerInsideTargetInterior() {
        // Aggressor (player ship) + player.
        Entity aggressor = new Entity();
        aggressor.add(new TransformComponent());
        engine.addEntity(aggressor);

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = aggressor;
        player.add(state);
        engine.addEntity(player);

        // VULNERABLE target with an interior + a marked player-aggressor operation.
        Entity target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(40f, 0f, 0f);
        target.add(tt);
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.active = false;
        target.add(interior);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.phase = BoardingPhase.VULNERABLE;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        attach.launchPod(aggressor, target);

        for (int i = 0; i < 200
            && target.getComponent(BoardingOperationComponent.class).phase != BoardingPhase.INTERIOR_COMBAT;
            i++) {
            engine.update(0.05f);
        }

        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(PlayerMode.ON_FOOT_INTERIOR, state.currentMode);
        assertSame(target, state.currentShip);
        assertTrue(target.getComponent(ShipInteriorComponent.class).active);
    }
}
