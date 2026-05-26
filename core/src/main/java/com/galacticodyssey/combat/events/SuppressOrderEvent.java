package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class SuppressOrderEvent {
    public final int squadId;
    public final Vector3 targetPosition;

    public SuppressOrderEvent(int squadId, Vector3 targetPosition) {
        this.squadId = squadId;
        this.targetPosition = new Vector3(targetPosition);
    }
}
