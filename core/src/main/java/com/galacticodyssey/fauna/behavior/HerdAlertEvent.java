package com.galacticodyssey.fauna.behavior;

import com.badlogic.gdx.math.Vector3;

public final class HerdAlertEvent {
    public final int spawnGroupId;
    public final Vector3 fleeFrom;

    public HerdAlertEvent(int spawnGroupId, Vector3 fleeFrom) {
        this.spawnGroupId = spawnGroupId;
        this.fleeFrom = new Vector3(fleeFrom);
    }
}
