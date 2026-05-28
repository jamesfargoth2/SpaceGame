package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.BoardingOperationSnapshot;

import java.util.UUID;

/**
 * Shared state for a single boarding operation. Lives on the boarding TARGET ship
 * (added lazily when the ship is first disabled), so the same systems run whether the
 * player or an NPC is the aggressor.
 */
public class BoardingOperationComponent implements Component, Snapshotable<BoardingOperationSnapshot> {

    public enum BoardingPhase {
        NONE,
        DISABLING,
        VULNERABLE,
        ATTACHING,
        BREACHED,
        INTERIOR_COMBAT,
        RESOLVING,
        RESOLVED
    }

    public enum AttachMethod { CLAMP, BREACH_POD }

    public BoardingPhase phase = BoardingPhase.NONE;
    public Entity aggressorShip;
    public Entity targetShip;
    public AttachMethod attachMethod;
    public Entity entryPoint;
    public boolean playerIsAggressor;

    // Persisted entity references (resolved by ReferenceResolver on load).
    public UUID aggressorShipId;
    public UUID targetShipId;
    public UUID entryPointId;

    @Override
    public BoardingOperationSnapshot takeSnapshot() {
        BoardingOperationSnapshot s = new BoardingOperationSnapshot();
        s.phase = phase.name();
        s.attachMethod = attachMethod == null ? null : attachMethod.name();
        s.playerIsAggressor = playerIsAggressor;
        s.aggressorShipId = aggressorShipId;
        s.targetShipId = targetShipId;
        s.entryPointId = entryPointId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(BoardingOperationSnapshot s) {
        phase = BoardingPhase.valueOf(s.phase);
        attachMethod = s.attachMethod == null ? null : AttachMethod.valueOf(s.attachMethod);
        playerIsAggressor = s.playerIsAggressor;
        aggressorShipId = s.aggressorShipId;
        targetShipId = s.targetShipId;
        entryPointId = s.entryPointId;
        // Entity references resolved later by ReferenceResolver.
    }
}
