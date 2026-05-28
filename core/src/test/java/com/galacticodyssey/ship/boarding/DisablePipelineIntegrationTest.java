package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.ShipBoardableEvent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingOrchestratorSystem;
import com.galacticodyssey.ship.boarding.systems.ShipSubsystemSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisablePipelineIntegrationTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity target;
    private final List<ShipBoardableEvent> boardable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ShipSubsystemSystem(eventBus));        // priority 9
        engine.addSystem(new BoardingOrchestratorSystem(eventBus)); // priority 1

        target = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        target.add(tc);
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 400f; data.currentHullHp = 400f;
        target.add(data);
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        target.add(subs);
        engine.addEntity(target);

        eventBus.subscribe(ShipBoardableEvent.class, boardable::add);
    }

    @Test
    void empBarrageToAftDisablesEnginesAndMakesBoardable() {
        // Aft hit (local z = -10 → ENGINES). Step two frames per hit so the
        // ShipSubsystemSystem -> SubsystemDisabledEvent -> BoardingOrchestratorSystem
        // chain fully propagates.
        for (int i = 0; i < 3; i++) {
            eventBus.publish(new ShipDamageEvent(
                target, null, 10f, DamageType.EMP, new Vector3(0, 0, -10)));
            engine.update(0.016f); // applies EMP, may publish SubsystemDisabledEvent
            engine.update(0.016f); // orchestrator consumes SubsystemDisabledEvent
        }

        ShipSubsystemsComponent subs = target.getComponent(ShipSubsystemsComponent.class);
        assertFalse(subs.enginesOperational(), "EMP barrage disables engines");
        BoardingOperationComponent op = target.getComponent(BoardingOperationComponent.class);
        assertNotNull(op);
        assertEquals(BoardingPhase.VULNERABLE, op.phase);
        assertFalse(boardable.isEmpty());
    }
}
