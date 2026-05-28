package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostileInteriorExitTest {

    @Test
    void healthyOwnShipIsNotHostile() {
        Entity ownShip = new Entity();
        assertNull(InteractionSystem.boardingHomeShip(ownShip),
            "a ship with no boarding op is the player's own ship — normal exit");
    }

    @Test
    void boardedHostileShipReturnsAggressorHomeShip() {
        Entity homeShip = new Entity();
        Entity hostile = new Entity();
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = hostile;
        op.aggressorShip = homeShip;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        hostile.add(op);

        assertSame(homeShip, InteractionSystem.boardingHomeShip(hostile),
            "exiting a boarded hostile ship returns the player to their aggressor ship");
    }

    @Test
    void resolvedOperationIsNotHostile() {
        Entity hostile = new Entity();
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = hostile;
        op.aggressorShip = new Entity();
        op.phase = BoardingPhase.RESOLVED;
        hostile.add(op);
        assertNull(InteractionSystem.boardingHomeShip(hostile));
    }
}
