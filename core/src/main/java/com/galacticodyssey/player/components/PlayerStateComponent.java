package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerStateSnapshot;
import java.util.UUID;

public class PlayerStateComponent implements Component, Snapshotable<PlayerStateSnapshot> {
    public enum PlayerMode {
        ON_FOOT_EXTERIOR,
        ON_FOOT_INTERIOR,
        PILOTING,
        DRIVING
    }

    public PlayerMode currentMode = PlayerMode.ON_FOOT_EXTERIOR;
    public Entity currentShip;
    public Entity currentVehicle;
    public Entity interactionTarget;

    /** Persisted alongside Entity references for save/load; resolved by ReferenceResolver. */
    public UUID currentShipId;
    public UUID currentVehicleId;
    public UUID interactionTargetId;

    @Override
    public PlayerStateSnapshot takeSnapshot() {
        PlayerStateSnapshot s = new PlayerStateSnapshot();
        s.currentMode = currentMode;
        s.currentShipId = currentShipId;
        s.interactionTargetId = interactionTargetId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(PlayerStateSnapshot s) {
        currentMode = s.currentMode;
        currentShipId = s.currentShipId;
        interactionTargetId = s.interactionTargetId;
        // Entity references (currentShip, interactionTarget) are resolved later by ReferenceResolver.
    }
}
