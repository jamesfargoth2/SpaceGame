package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class RetreatOrderEvent {
    public final int squadId;
    public final Vector3 retreatPoint;

    public RetreatOrderEvent(int squadId, Vector3 retreatPoint) {
        this.squadId = squadId;
        this.retreatPoint = new Vector3(retreatPoint);
    }
}
