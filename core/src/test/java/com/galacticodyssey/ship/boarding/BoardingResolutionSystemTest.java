package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingResolutionSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity target;
    private final List<BoardingResolvedEvent> resolved = new ArrayList<>();
    private final List<ReputationChangeEvent> repChanges = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingResolutionSystem(eventBus, null));

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerWalletComponent());
        player.add(new CargoBayComponent());
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);

        target = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 400f; data.currentHullHp = 400f;
        target.add(data);
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        subs.get(SubsystemType.ENGINES).empDisableTimer = 5f; // disabled
        target.add(subs);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.factionId = "pirates";
        target.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.aggressorShip = new Entity();
        op.phase = BoardingPhase.RESOLVING;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        eventBus.subscribe(BoardingResolvedEvent.class, resolved::add);
        eventBus.subscribe(ReputationChangeEvent.class, repChanges::add);
    }

    @Test
    void hijackFlipsOwnershipAndRestoresEngines() {
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);

        OwnedShipComponent owned = target.getComponent(OwnedShipComponent.class);
        assertNotNull(owned);
        assertEquals(Owner.PLAYER, owned.owner);
        assertTrue(target.getComponent(ShipSubsystemsComponent.class).enginesOperational(),
            "hijack restores engine operation");
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
        assertEquals(BoardingPhase.RESOLVED, target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(1, resolved.size());
        assertEquals(BoardingOutcome.HIJACK, resolved.get(0).outcome);
    }

    @Test
    void scrapAddsCargoCreditsAndRemovesShip() {
        long before = player.getComponent(PlayerWalletComponent.class).credits;
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.SCRAP));
        engine.update(0.016f);

        assertFalse(player.getComponent(CargoBayComponent.class).contents.isEmpty(),
            "scrap deposits materials");
        assertTrue(player.getComponent(PlayerWalletComponent.class).credits > before,
            "scrap yields credits");
        assertEquals(0f, target.getComponent(ShipDataComponent.class).currentHullHp, 0.01f,
            "scrapped ship hull marked destroyed");
        assertEquals(1, resolved.size());
    }

    @Test
    void ransomAwardsCreditsAndReputation() {
        long before = player.getComponent(PlayerWalletComponent.class).credits;
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.RANSOM));
        engine.update(0.016f);

        assertTrue(player.getComponent(PlayerWalletComponent.class).credits > before);
        assertEquals(1, repChanges.size());
        assertEquals("pirates", repChanges.get(0).factionId);
        assertEquals(1, resolved.size());
        assertEquals(BoardingOutcome.RANSOM, resolved.get(0).outcome);
    }

    @Test
    void towStoresShipInGarage() {
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.TOW));
        engine.update(0.016f);

        assertEquals(Owner.PLAYER, target.getComponent(OwnedShipComponent.class).owner);
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
        assertEquals("TOW", player.getComponent(PlayerGarageComponent.class).ships.get(0).acquiredVia);
    }

    @Test
    void hijackGateRejectsLowTactics() {
        assertFalse(BoardingResolutionSystem.hijackSucceeds(/*tactics*/ 1, /*required*/ 50));
        assertTrue(BoardingResolutionSystem.hijackSucceeds(/*tactics*/ 60, /*required*/ 50));
    }

    @Test
    void hijackGateBoundary() {
        // Below requirement fails; meeting it exactly (the boundary) succeeds.
        assertFalse(BoardingResolutionSystem.hijackSucceeds(/*tactics*/ 0, /*required*/ 1));
        assertTrue(BoardingResolutionSystem.hijackSucceeds(/*tactics*/ 1, /*required*/ 1));
    }
}
