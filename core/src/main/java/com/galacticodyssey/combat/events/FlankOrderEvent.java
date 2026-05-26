package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class FlankOrderEvent {
    public final int squadId;
    public final Vector3 targetPosition;
    public final Vector3 flankDirection;

    public FlankOrderEvent(int squadId, Vector3 targetPosition, Vector3 flankDirection) {
        this.squadId = squadId;
        this.targetPosition = new Vector3(targetPosition);
        this.flankDirection = new Vector3(flankDirection);
    }
}
