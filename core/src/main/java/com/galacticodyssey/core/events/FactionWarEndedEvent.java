package com.galacticodyssey.core.events;

public final class FactionWarEndedEvent {
    public final String warId;

    public FactionWarEndedEvent(String warId) {
        this.warId = warId;
    }
}
