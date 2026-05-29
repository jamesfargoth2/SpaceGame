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
        op.playerIsAggressor = true;
        hostile.add(op);

        assertSame(homeShip, InteractionSystem.boardingHomeShip(hostile),
            "exiting a boarded hostile ship returns the player to their aggressor ship");
    }

    @Test
    void npcBoardingPlayerDoesNotRouteToAggressor() {
        // NPC is boarding the player's ship: the op lives on the player's ship with
        // aggressorShip = the NPC and playerIsAggressor = false. Exiting must use the normal
        // exit path (return null), not route the player toward the NPC aggressor.
        Entity npc = new Entity();
        Entity playerShip = new Entity();
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = playerShip;
        op.aggressorShip = npc;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        op.playerIsAggressor = false;
        playerShip.add(op);

        assertNull(InteractionSystem.boardingHomeShip(playerShip),
            "NPC-boards-player op must not route the player toward the NPC aggressor on exit");
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
