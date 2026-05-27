package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import java.util.UUID;

public class PlayerStateSnapshot {
    public PlayerMode currentMode;
    public UUID currentShipId;
    public UUID interactionTargetId;
    public PlayerStateSnapshot() {}
}
