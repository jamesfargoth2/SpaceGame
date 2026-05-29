package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingHijackTacticsTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private PlayerStatsComponent stats;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingResolutionSystem(eventBus, null));
        player = new Entity();
        player.add(new PlayerTagComponent());
        stats = new PlayerStatsComponent();
        player.add(stats);
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);
    }

    private Entity largeTarget() {
        Entity target = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 3000f; data.currentHullHp = 3000f;
        data.blueprint = new ShipBlueprint(42L, ShipSizeClass.LARGE);
        target.add(data);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.phase = BoardingPhase.RESOLVING;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);
        return target;
    }

    @Test
    void lowTacticsFailsLargeShipHijack() {
        stats.pointSkills.put(PointSkill.TACTICS, 5); // below LARGE requirement
        Entity target = largeTarget();
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);
        assertNull(target.getComponent(OwnedShipComponent.class),
            "failed hijack does not transfer ownership");
        assertEquals(0, player.getComponent(PlayerGarageComponent.class).ships.size());
        assertEquals(BoardingPhase.RESOLVED, target.getComponent(BoardingOperationComponent.class).phase,
            "operation still resolves (no soft-lock)");
    }

    @Test
    void highTacticsSucceedsLargeShipHijack() {
        stats.pointSkills.put(PointSkill.TACTICS, 50); // meets LARGE requirement
        Entity target = largeTarget();
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);
        assertEquals(Owner.PLAYER, target.getComponent(OwnedShipComponent.class).owner);
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
    }

    @Test
    void requiredTacticsScalesWithSize() {
        assertEquals(0, BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.SMALL));
        assertTrue(BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.MEDIUM)
            > BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.SMALL));
        assertTrue(BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.LARGE)
            > BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.MEDIUM));
    }
}
