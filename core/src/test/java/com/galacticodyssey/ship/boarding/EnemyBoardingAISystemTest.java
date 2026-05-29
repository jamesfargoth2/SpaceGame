package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.EnemyBoardingAISystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnemyBoardingAISystemTest {

    private EventBus eventBus;
    private Engine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
    }

    private Entity npcShipAt(float x) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
        e.add(t);
        engine.addEntity(e);
        return e;
    }

    private Entity disabledPlayerShipAt(float x) {
        Entity ship = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
        ship.add(t);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = ship;
        op.phase = BoardingPhase.VULNERABLE; // player ship disabled
        ship.add(op);
        engine.addEntity(ship);
        // a player piloting this ship
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        com.galacticodyssey.player.components.PlayerStateComponent ps =
            new com.galacticodyssey.player.components.PlayerStateComponent();
        ps.currentShip = ship;
        player.add(ps);
        engine.addEntity(player);
        return ship;
    }

    @Test
    void hostileNpcBoardsDisabledPlayerShip() {
        BoardingAttachSystem attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(eventBus, attach);
        engine.addSystem(ai);

        Entity playerShip = disabledPlayerShipAt(0);
        Entity npc = npcShipAt(40);

        engine.update(0.016f);

        BoardingOperationComponent op = playerShip.getComponent(BoardingOperationComponent.class);
        assertFalse(op.playerIsAggressor, "NPC is the aggressor when boarding the player");
        assertSame(npc, op.aggressorShip);
        assertEquals(1, engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get()).size(),
            "NPC launches a breaching pod at the player");
    }

    @Test
    void noNpcInRangeDoesNothing() {
        BoardingAttachSystem attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        engine.addSystem(new EnemyBoardingAISystem(eventBus, attach));

        Entity playerShip = disabledPlayerShipAt(0);
        npcShipAt(5000); // far away

        engine.update(0.016f);

        assertEquals(0, engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get()).size());
    }
}
