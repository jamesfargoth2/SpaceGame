package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
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

    @Test
    void entryWorldPositionRespectsTargetRotation() {
        // Rotate the target ship 90° about Y; entry math must use the full rotated+translated
        // transform (entryWorld = entryLocal · shipMat), not a plain position offset.
        TransformComponent tt = targetShip.getComponent(TransformComponent.class);
        tt.rotation.setFromAxis(0f, 1f, 0f, 90f);

        Vector3 entryLocal = new Vector3(1f, 0f, 2f);
        eventBus.publish(new ShipBreachedEvent(aggressorShip, targetShip, AttachMethod.CLAMP,
            new Vector3(entryLocal)));
        engine.update(0.016f);

        // Expected: build the same ship matrix and transform the local entry point through it.
        Matrix4 shipMat = new Matrix4().set(tt.position, tt.rotation);
        Vector3 expected = new Vector3(entryLocal).mul(shipMat);

        Vector3 actual = player.getComponent(TransformComponent.class).position;
        assertEquals(expected.x, actual.x, 1e-4f, "rotated entry world x");
        assertEquals(expected.y, actual.y, 1e-4f, "rotated entry world y");
        assertEquals(expected.z, actual.z, 1e-4f, "rotated entry world z");
        // Sanity: 90°-about-Y maps local (1,0,2) → (2,0,-1), then +translation (100,0,0).
        assertEquals(102f, actual.x, 1e-4f);
        assertEquals(0f, actual.y, 1e-4f);
        assertEquals(-1f, actual.z, 1e-4f);
    }
}
