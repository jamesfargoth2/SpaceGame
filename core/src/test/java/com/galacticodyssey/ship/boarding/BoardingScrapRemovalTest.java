package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingScrapRemovalTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingResolutionSystem(eventBus, null)); // null main world (headless)
        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerWalletComponent());
        player.add(new CargoBayComponent());
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);
    }

    @Test
    void scrapRemovesShipEntityFromEngine() {
        Entity target = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 400f; data.currentHullHp = 400f;
        target.add(data);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.phase = BoardingPhase.RESOLVING;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);
        int before = engine.getEntities().size();

        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.SCRAP));
        engine.update(0.016f);

        assertFalse(player.getComponent(CargoBayComponent.class).contents.isEmpty(),
            "scrap still deposits materials");
        assertEquals(before - 1, engine.getEntities().size(), "scrapped ship removed from engine");
    }
}
