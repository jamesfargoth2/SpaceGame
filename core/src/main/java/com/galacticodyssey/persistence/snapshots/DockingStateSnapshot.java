package com.galacticodyssey.persistence.snapshots;

import java.util.UUID;

public class DockingStateSnapshot {
    public String dockingPhase;
    public UUID targetEntityId;
    public float approachAxisX;
    public float approachAxisY;
    public float approachAxisZ;
    public float coneHalfAngleDeg;
    public float maxApproachSpeed;
    public DockingStateSnapshot() {}
}
