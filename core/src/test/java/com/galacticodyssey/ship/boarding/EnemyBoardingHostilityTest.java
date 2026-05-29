package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.EnemyBoardingAISystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnemyBoardingHostilityTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
    }

    private Entity npcShip(float x, String factionId) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
        e.add(t);
        ShipDataComponent d = new ShipDataComponent();
        d.hullHp = 200f; d.currentHullHp = 200f;
        e.add(d);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.factionId = factionId;
        e.add(def);
        engine.addEntity(e);
        return e;
    }

    private Entity disabledPlayerShip() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ShipDataComponent d = new ShipDataComponent();
        d.hullHp = 200f; d.currentHullHp = 200f;
        ship.add(d);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = ship;
        op.phase = BoardingPhase.VULNERABLE;
        ship.add(op);
        engine.addEntity(ship);
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentShip = ship;
        player.add(ps);
        engine.addEntity(player);
        return ship;
    }

    private int podCount() {
        return engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get()).size();
    }

    @Test
    void hostileFactionShipBoardsThePlayer() {
        // Standing -80 < -50 → hostile.
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(attach, factionId -> -80f);
        engine.addSystem(ai);
        disabledPlayerShip();
        npcShip(40, "pirates");
        engine.update(0.016f);
        assertEquals(1, podCount(), "hostile faction launches a boarding pod");
    }

    @Test
    void neutralFactionShipDoesNotBoard() {
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(attach, factionId -> 0f); // neutral
        engine.addSystem(ai);
        disabledPlayerShip();
        npcShip(40, "traders");
        engine.update(0.016f);
        assertEquals(0, podCount(), "neutral faction does not board");
    }

    @Test
    void nullQueryFallsBackToHostile() {
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(attach, null); // no reputation wired
        engine.addSystem(ai);
        disabledPlayerShip();
        npcShip(40, "anyone");
        engine.update(0.016f);
        assertEquals(1, podCount(), "with no reputation source, any ship is treated as hostile");
    }
}
