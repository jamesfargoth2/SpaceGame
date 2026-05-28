package com.galacticodyssey.stealth.events;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.stealth.AwarenessState;

public final class AwarenessChangedEvent {
    public final String npcId;
    public final AwarenessState oldState;
    public final AwarenessState newState;
    public final Vector3 lastKnownPosition; // defensive copy

    public AwarenessChangedEvent(String npcId, AwarenessState oldState, AwarenessState newState,
                                 Vector3 lastKnownPosition) {
        this.npcId = npcId;
        this.oldState = oldState;
        this.newState = newState;
        this.lastKnownPosition = new Vector3(lastKnownPosition);
    }
}
