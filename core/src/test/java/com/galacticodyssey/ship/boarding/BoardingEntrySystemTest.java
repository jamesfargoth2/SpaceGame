package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingEntrySystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingEntrySystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity aggressorShip;
    private Entity targetShip;
    private final List<PlayerEnteredHostileInteriorEvent> entered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingEntrySystem(eventBus, null));

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        player.add(state);
        engine.addEntity(player);

        aggressorShip = new Entity();
        aggressorShip.add(new TransformComponent());
        engine.addEntity(aggressorShip);

        targetShip = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(100f, 0f, 0f);
        targetShip.add(tt);
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.active = false;
        targetShip.add(interior);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = targetShip;
        op.aggressorShip = aggressorShip;
        op.phase = BoardingPhase.BREACHED;
        op.playerIsAggressor = true;
        targetShip.add(op);
        engine.addEntity(targetShip);

        // Player pilots the aggressor.
        state.currentShip = aggressorShip;

        eventBus.subscribe(PlayerEnteredHostileInteriorEvent.class, entered::add);
    }

    @Test
    void breachWithPlayerAggressorMovesPlayerIntoTargetInterior() {
        eventBus.publish(new ShipBreachedEvent(aggressorShip, targetShip, AttachMethod.CLAMP,
            new Vector3(1f, 0f, 2f)));
        engine.update(0.016f);

        PlayerStateComponent state = player.getComponent(PlayerStateComponent.class);
        assertEquals(PlayerMode.ON_FOOT_INTERIOR, state.currentMode);
        assertSame(targetShip, state.currentShip);
        assertTrue(targetShip.getComponent(ShipInteriorComponent.class).active,
            "target interior activated");
        // Entry world pos = targetPos (100,0,0) + entryLocal (1,0,2).
        assertEquals(new Vector3(101f, 0f, 2f), player.getComponent(TransformComponent.class).position);
        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            targetShip.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(1, entered.size());
        assertSame(targetShip, entered.get(0).targetShip);
    }
}
