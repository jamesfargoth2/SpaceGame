package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipBoardableEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Owns boarding phase-transition rules only. In Plan A it handles the single transition
 * NONE/absent → VULNERABLE when a ship's ENGINES are disabled, lazily attaching a
 * {@link BoardingOperationComponent} to the target. Later plans add ATTACHING..RESOLVED.
 */
public class BoardingOrchestratorSystem extends EntitySystem {

    public static final int PRIORITY = 1;

    private final EventBus eventBus;
    private final Queue<SubsystemDisabledEvent> pending = new ArrayDeque<>();

    public BoardingOrchestratorSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(SubsystemDisabledEvent.class, pending::add);
    }

    @Override
    public void update(float deltaTime) {
        SubsystemDisabledEvent event;
        while ((event = pending.poll()) != null) {
            if (event.subsystem != SubsystemType.ENGINES) continue;
            onEnginesDisabled(event.ship);
        }
    }

    private void onEnginesDisabled(Entity ship) {
        BoardingOperationComponent op = ship.getComponent(BoardingOperationComponent.class);
        if (op == null) {
            op = new BoardingOperationComponent();
            op.targetShip = ship;
            ship.add(op);
        }
        if (op.phase == BoardingPhase.NONE || op.phase == BoardingPhase.DISABLING) {
            op.phase = BoardingPhase.VULNERABLE;
            // aggressor is wired in Plan B (from the attach context); null is acceptable here.
            eventBus.publish(new ShipBoardableEvent(ship, op.aggressorShip));
        }
    }
}
