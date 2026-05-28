package com.galacticodyssey.stealth.events;

import com.galacticodyssey.stealth.AwarenessState;

public final class StealthHUDUpdateEvent {
    public final AwarenessState highestNearbyState;
    public final float nearestThreatDistance; // -1 if no NPCs nearby

    public StealthHUDUpdateEvent(AwarenessState highestNearbyState, float nearestThreatDistance) {
        this.highestNearbyState = highestNearbyState;
        this.nearestThreatDistance = nearestThreatDistance;
    }
}
