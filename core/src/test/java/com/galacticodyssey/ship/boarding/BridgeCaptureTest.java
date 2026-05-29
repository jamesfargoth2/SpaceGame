package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.InteriorLayout;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.BoardingClearedEvent;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BridgeCaptureTest {

    @Test
    void playerInBridgeWithNoDefendersThereCaptures() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        engine.addSystem(new BoardingCombatSystem(eventBus));

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        engine.addEntity(player);

        Entity aggressor = new Entity();
        aggressor.add(new TransformComponent());
        engine.addEntity(aggressor);

        // Target at origin; bridge local center far from the defender fan-out (which is near origin).
        Entity target = new Entity();
        target.add(new TransformComponent());
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.layout = layoutWithBridgeAt(new com.badlogic.gdx.math.Vector3(50f, 0f, 0f));
        target.add(interior);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 2;
        target.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.aggressorShip = aggressor;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        List<BoardingClearedEvent> cleared = new ArrayList<>();
        eventBus.subscribe(BoardingClearedEvent.class, cleared::add);

        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f); // spawns 2 defenders near origin; bridge at (50,0,0)

        // Defenders are alive but near origin — not in the bridge. Move player into the bridge.
        player.getComponent(TransformComponent.class).position.set(50f, 0f, 0f);
        engine.update(0.016f);

        assertEquals(1, cleared.size(), "bridge captured: player at bridge, no defenders there");
        assertEquals(BoardingPhase.RESOLVING,
            target.getComponent(BoardingOperationComponent.class).phase);
    }

    /** Builds a minimal InteriorLayout whose pilotSeatPosition is the bridge center. */
    private InteriorLayout layoutWithBridgeAt(com.badlogic.gdx.math.Vector3 bridge) {
        return new InteriorLayout(
            new ArrayList<>(),                                  // rooms
            new boolean[1][1][1],                               // corridorCells
            new com.badlogic.gdx.math.Vector3(0, 0, 0),         // airlockPosition
            bridge,                                             // pilotSeatPosition (bridge)
            new float[0], new short[0],                         // floor verts/indices
            new float[0], new short[0],                         // wall verts/indices
            1, 1, 1);                                           // grid sizes
    }
}
