package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.systems.*;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FullBoardingPipelineTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        // Plan A
        engine.addSystem(new ShipSubsystemSystem(eventBus));
        engine.addSystem(new BoardingOrchestratorSystem(eventBus));
        // Plan B
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        engine.addSystem(new BoardingEntrySystem(eventBus, null));
        // Plan C
        engine.addSystem(new BoardingCombatSystem(eventBus));
        engine.addSystem(new BoardingResolutionSystem(eventBus));
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) engine.update(0.05f);
    }

    @Test
    void playerBoardsDisablesClearsAndHijacks() {
        // Player + aggressor ship.
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
        player.add(new PlayerWalletComponent());
        player.add(new CargoBayComponent());
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);

        // Target ship with subsystems, interior, defenders.
        Entity target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(30f, 0f, 0f);
        target.add(tt);
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 300f; data.currentHullHp = 300f;
        target.add(data);
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        target.add(subs);
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.active = false;
        target.add(interior);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 2; def.defenderHealth = 30f;
        target.add(def);
        engine.addEntity(target);

        // 1. Disable engines via EMP to the aft.
        for (int i = 0; i < 3; i++) {
            eventBus.publish(new ShipDamageEvent(target, aggressor, 10f, DamageType.EMP,
                new Vector3(30f, 0f, -10f)));
            engine.update(0.016f);
            engine.update(0.016f);
        }
        assertEquals(BoardingPhase.VULNERABLE,
            target.getComponent(BoardingOperationComponent.class).phase);

        // 2. Launch a breach pod; fly to BREACHED, then entry → INTERIOR_COMBAT.
        attach.launchPod(aggressor, target);
        // BoardingInitiationSystem sets this in-game; set directly here since this test calls launchPod directly
        target.getComponent(BoardingOperationComponent.class).playerIsAggressor = true;
        step(120);
        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(PlayerMode.ON_FOOT_INTERIOR, state.currentMode);

        // 3. Kill all defenders → RESOLVING.
        ImmutableArray<Entity> defenders = engine.getEntitiesFor(
            Family.all(BoardingDefenderComponent.class).get());
        assertEquals(2, defenders.size());
        for (int i = 0; i < defenders.size(); i++) {
            Entity d = defenders.get(i);
            d.getComponent(HealthComponent.class).alive = false;
            eventBus.publish(new EntityKilledEvent(d, player));
        }
        engine.update(0.016f);
        assertEquals(BoardingPhase.RESOLVING,
            target.getComponent(BoardingOperationComponent.class).phase);

        // 4. Choose hijack → RESOLVED + player owns the ship.
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);
        assertEquals(BoardingPhase.RESOLVED,
            target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(Owner.PLAYER, target.getComponent(OwnedShipComponent.class).owner);
        assertTrue(target.getComponent(ShipSubsystemsComponent.class).enginesOperational(),
            "hijack restored engines");
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
    }
}
