package com.galacticodyssey.stealth.events;

public final class PlayerDetectedEvent {
    public final String detectorId;
    public final String detectorType; // "SHIP_PASSIVE" or "SHIP_SCAN"

    public PlayerDetectedEvent(String detectorId, String detectorType) {
        this.detectorId = detectorId;
        this.detectorType = detectorType;
    }
}
