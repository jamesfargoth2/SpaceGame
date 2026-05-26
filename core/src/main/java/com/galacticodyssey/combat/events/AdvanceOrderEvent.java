package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class AdvanceOrderEvent {
    public final int squadId;
    public final Vector3 targetPosition;

    public AdvanceOrderEvent(int squadId, Vector3 targetPosition) {
        this.squadId = squadId;
        this.targetPosition = new Vector3(targetPosition);
    }
}
