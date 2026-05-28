package com.galacticodyssey.core.events;

public class FactionWarEndedEvent {
    public final String warId;

    public FactionWarEndedEvent(String warId) {
        this.warId = warId;
    }
}
